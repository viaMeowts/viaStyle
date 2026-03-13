"""
viaStyle Scarpet Script Generator

Generates Scarpet (.sc) scripts that integrate with the viaStyle mod
via the PLAYER_MESSAGE event (requires useScarpetEvents = true in config
and fabric-carpet installed on the server).

Usage:
    python scarpet_generator.py [--all] [--out <dir>]

    --all       generate all scripts without prompting
    --out <dir> output directory (default: generated_scripts/)

Each generated script can be loaded in-game with:
    /script load <script_name>
"""

import argparse
import os
import textwrap

OUTPUT_DIR_DEFAULT = os.path.join(os.path.dirname(__file__), "generated_scripts")

SCRIPTS = {
    "logger":   "chat logger      — log all messages to a file with timestamps",
    "antispam": "anti-spam        — rate-limit players who send messages too fast",
    "filter":   "chat filter      — block or censor banned words",
    "discord":  "discord webhook  — relay chat to Discord via a queue file",
    "welcome":  "welcome message  — greet players on join with a configurable motd",
    "commands": "chat commands    — hash-prefixed in-chat commands (#help, #roll, ...)",
    "stats":    "chat statistics  — track per-player message and word counts",
}

HEADER = """\
// {filename}
// {description}
//
// Installation:
//   copy to: <world>/scripts/{basename}
//   in-game:  /script load {name}
//
// Requirements:
//   viaStyle with useScarpetEvents = true in config/viaStyle/viaStyle.toml
//   fabric-carpet installed on the server

"""


def header(name, filename, description):
    return HEADER.format(
        filename=filename,
        description=description,
        basename=filename,
        name=name,
    )


# ---------------------------------------------------------------------------
# Script bodies
# ---------------------------------------------------------------------------

def script_logger():
    name = "viastyle_logger"
    filename = name + ".sc"
    desc = "Logs all chat messages to a file with timestamps."
    body = textwrap.dedent("""\
        __config() -> {
            'scope'       -> 'global',
            'stay_loaded' -> true
        };

        global_log_file = 'chat_log.txt';

        _fmt_time() -> (
            t = convert_date(unix_time());
            str('%04d-%02d-%02d %02d:%02d:%02d', t:0, t:1, t:2, t:3, t:4, t:5)
        );

        __on_player_message(player, message) -> (
            line = str('[%s] %s: %s', _fmt_time(), player~'name', message);
            write_file(global_log_file, 'a', line);
            logger('info', line)
        );
    """)
    return filename, header(name, filename, desc) + body


def script_antispam():
    name = "viastyle_antispam"
    filename = name + ".sc"
    desc = "Rate-limits chat messages per player."
    body = textwrap.dedent("""\
        __config() -> {
            'scope'       -> 'global',
            'stay_loaded' -> true
        };

        // Maximum messages allowed within the time window.
        global_limit  = 5;
        // Time window in seconds.
        global_window = 10;
        // Message shown to the player when muted.
        global_warn   = 'Slow down.';

        // Map: player name -> list of unix timestamps of recent messages.
        global_timestamps = {};

        __on_player_message(player, message) -> (
            name = player~'name';
            now  = unix_time() / 1000;

            if (!has(global_timestamps, name),
                global_timestamps:name = []
            );

            times = global_timestamps:name;
            // Drop entries outside the window.
            times = filter(times, _ > now - global_window);
            global_timestamps:name = times;

            if (length(times) >= global_limit,
                player(name, 'say_as_server', global_warn);
                return('cancel')
            );

            global_timestamps:name += [now];
            null
        );
    """)
    return filename, header(name, filename, desc) + body


def script_filter():
    name = "viastyle_filter"
    filename = name + ".sc"
    desc = "Blocks or censors banned words."
    body = textwrap.dedent("""\
        __config() -> {
            'scope'       -> 'global',
            'stay_loaded' -> true
        };

        // Set to true to block the message entirely, false to censor with asterisks.
        global_block_on_match = false;
        // List of banned words (lowercase).
        global_banned = ['badword1', 'badword2', 'example'];

        _censor(word) -> str('%s%s', word:0, reduce(range(length(word) - 1), _a + '*', ''));

        __on_player_message(player, message) -> (
            lower = lower(message);
            matched = false;
            result  = message;

            for (global_banned,
                if (str_index(lower, _) != -1,
                    matched = true;
                    if (!global_block_on_match,
                        result = replace_first(result, _, _censor(_))
                    )
                )
            );

            if (matched && global_block_on_match,
                return('cancel')
            );

            if (matched,
                // Return the censored version.
                // viaStyle will use this value as the displayed message.
                return(result)
            );

            null
        );
    """)
    return filename, header(name, filename, desc) + body


def script_discord():
    name = "viastyle_discord"
    filename = name + ".sc"
    desc = "Appends chat messages to a queue file for external Discord relay."
    body = textwrap.dedent("""\
        __config() -> {
            'scope'       -> 'global',
            'stay_loaded' -> true
        };

        // Path of the queue file, relative to the world folder.
        // An external process (e.g. a Python bot) reads and clears this file.
        global_queue_file = 'discord_queue.txt';

        _fmt_time() -> (
            t = convert_date(unix_time());
            str('%04d-%02d-%02d %02d:%02d:%02d', t:0, t:1, t:2, t:3, t:4, t:5)
        );

        __on_player_message(player, message) -> (
            line = str('[MC][%s] %s: %s', _fmt_time(), player~'name', message);
            write_file(global_queue_file, 'a', line)
        );
    """)
    return filename, header(name, filename, desc) + body


def script_welcome():
    name = "viastyle_welcome"
    filename = name + ".sc"
    desc = "Sends a welcome message to players when they join."
    body = textwrap.dedent("""\
        __config() -> {
            'scope'       -> 'global',
            'stay_loaded' -> true
        };

        // Lines sent to the joining player as private messages.
        global_motd = [
            'Welcome to the server.',
            'Type ! before your message to use global chat.',
            'Type /help for a list of commands.'
        ];

        __on_player_connects(player) -> (
            name = player~'name';
            for (global_motd,
                run(str('tellraw %s {"text":"%s","color":"yellow"}', name, _))
            )
        );
    """)
    return filename, header(name, filename, desc) + body


def script_commands():
    name = "viastyle_commands"
    filename = name + ".sc"
    desc = "Adds simple hash-prefixed in-chat commands (#help, #roll, #tps)."
    body = textwrap.dedent("""\
        __config() -> {
            'scope'       -> 'global',
            'stay_loaded' -> true
        };

        _cmd_help(player) -> (
            for (['#help — show this list', '#roll — roll a random number 1-100', '#tps — show server TPS'],
                run(str('tellraw %s {"text":"%s","color":"gray"}', player~'name', _))
            )
        );

        _cmd_roll(player) -> (
            n = rand(100) + 1;
            run(str('tellraw @a {"text":"%s rolled %d"}', player~'name', n))
        );

        _cmd_tps(player) -> (
            t = server_info('tps');
            run(str('tellraw %s {"text":"TPS: %s","color":"green"}', player~'name', t))
        );

        __on_player_message(player, message) -> (
            if (message == '#help',   _cmd_help(player);  return('cancel'));
            if (message == '#roll',   _cmd_roll(player);  return('cancel'));
            if (message == '#tps',    _cmd_tps(player);   return('cancel'));
            null
        );
    """)
    return filename, header(name, filename, desc) + body


def script_stats():
    name = "viastyle_stats"
    filename = name + ".sc"
    desc = "Tracks per-player message and word counts. Query with #stats."
    body = textwrap.dedent("""\
        __config() -> {
            'scope'       -> 'global',
            'stay_loaded' -> true
        };

        global_msg_count  = {};
        global_word_count = {};

        __on_player_message(player, message) -> (
            name  = player~'name';
            words = length(split(' ', message));

            global_msg_count:name  = (global_msg_count:name  ?: 0) + 1;
            global_word_count:name = (global_word_count:name ?: 0) + words;

            if (message == '#stats',
                msgs  = global_msg_count:name;
                wds   = global_word_count:name;
                run(str(
                    'tellraw %s {"text":"messages: %d  words: %d","color":"aqua"}',
                    name, msgs, wds
                ));
                return('cancel')
            );

            null
        );
    """)
    return filename, header(name, filename, desc) + body


ALL_GENERATORS = [
    script_logger,
    script_antispam,
    script_filter,
    script_discord,
    script_welcome,
    script_commands,
    script_stats,
]


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def write_script(out_dir, filename, content):
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, filename)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"  written: {path}")


def interactive(out_dir):
    keys = list(SCRIPTS.keys())
    while True:
        print()
        for i, key in enumerate(keys, 1):
            print(f"  {i}  {SCRIPTS[key]}")
        print(f"  {len(keys) + 1}  generate all")
        print("  0  exit")
        print()
        choice = input("select: ").strip()
        if choice == "0":
            break
        if choice == str(len(keys) + 1):
            for gen in ALL_GENERATORS:
                filename, content = gen()
                write_script(out_dir, filename, content)
            continue
        try:
            idx = int(choice) - 1
            if 0 <= idx < len(keys):
                filename, content = ALL_GENERATORS[idx]()
                write_script(out_dir, filename, content)
            else:
                print("  invalid choice")
        except ValueError:
            print("  invalid choice")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--all", action="store_true", help="generate all scripts without prompting")
    parser.add_argument("--out", default=OUTPUT_DIR_DEFAULT, metavar="DIR", help="output directory")
    args = parser.parse_args()

    if args.all:
        for gen in ALL_GENERATORS:
            filename, content = gen()
            write_script(args.out, filename, content)
    else:
        interactive(args.out)


if __name__ == "__main__":
    main()
