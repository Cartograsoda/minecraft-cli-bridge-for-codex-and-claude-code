> **Notice**: This mod is an experimental prototype provided as-is without warranty. The name of the product is **Minecraft CLI Bridge for Codex and Claude** (`claude-codex-chat`).

# claude-codex-chat

> A multi-agent coding client inside Minecraft, built by a software review and reasoning council.

`claude-codex-chat` is a **100% client-side Fabric mod for Minecraft 26.1.2** that turns Minecraft into an alternate frontend for **Claude Code** and **OpenAI Codex** running locally on your machine.

It is not a Minecraft bot.

Claude does not mine diamonds.  
Codex does not fight creepers.  
Neither can inspect your world, read inventory, move the player, place blocks, use Minecraft screenshots, or automate gameplay.

Minecraft is simply the interface.

The goal is straightforward:

> **If I instinctively reach for Alt+Tab because I need something from Claude Code or Codex, the Minecraft client is missing a feature.**

---

## Why this exists

Supervising autonomous coding agents involves reviewing traces, inspecting diffs, modifying project configurations, running test suites, and waiting for multi-minute turns to finish.

Which led to a very simple insight:

> Why alt-tab out of Minecraft every two minutes to check on agent runs?

What began as:

```text
Minecraft chat
    ↓
Claude prompt
    ↓
response in chat
```

slowly evolved into:

```text
Minecraft
    │
    ├── HUD telemetry
    ├── AI composer
    ├── session browser
    ├── transcript viewer
    ├── task manager
    ├── diff viewer
    ├── project profiles
    └── multi-agent supervisor
            │
            ├── Claude Code
            └── Codex
```

At this stage, it operates as a full coding client interface.

---



# Strict non-interference

Coding agents do **not** receive any Minecraft world or gameplay control surface.

They cannot:
- move the player
- inspect inventory, blocks, or entities
- place or break blocks
- read Minecraft world state
- use screenshots
- automate gameplay
- interact with connected Minecraft servers

There is no world manipulation API. The data flow is strictly between the coding agents and your local development directory.

---

# Zero server footprint

The mod is 100% client-side. AI commands and chat output are intercepted locally using Fabric client events and client command trees, and are never transmitted over the network to multiplayer servers.

Compatible with:
- Vanilla servers
- Fabric / Paper / Spigot / Forge servers
- Multiplayer LAN & realms

---

# Native sessions

The mod preserves native provider session continuity. Sessions started in your regular terminal can be resumed inside Minecraft, and sessions modified in Minecraft remain fully accessible from your standard terminal CLIs afterwards.

---

# Multi-agent supervision

Multiple agent instances can run concurrently per provider:

```text
CLAUDE
├── auth-refactor        ● RUNNING
├── frontend-polish      ● RUNNING
└── deployment           ○ IDLE

CODEX
├── backend-review       ● RUNNING
└── test-review          ⚠ INPUT REQUIRED
```

Each instance maintains its own dedicated state:
- Native session ID
- Working directory & Git worktree
- Model & reasoning effort
- Permission mode
- Prompt queue & stashes
- Activity transcript & diffs
- Telemetry & background tasks

---

# HUD

The HUD provides unobtrusive, real-time monitoring of active agents with multiple density modes:

```text
OFF
COMPACT
NORMAL
DETAILED
```

Typical compact HUD display:
```text
CLAUDE 2● 1○ │ CODEX 1● 1⚠
FOCUS Claude/auth │ CTX 61% │ PLAN
```

Failsafe policy: **Unknown telemetry displays as `--` rather than fabricating simulated estimates.**

---

# AI Composer

Open the dedicated composer screen at any time (Default key: `U`).

Features:
- Multiline input & clipboard paste
- Per-project prompt history (`Ctrl+R`)
- Draft persistence & named stashes (`Ctrl+S`)
- Live target-agent switching (`Ctrl+Tab`)
- Prompt queueing & instant interrupt/steer
- Inline shell execution mode
- Model picker & permission toggles

---

# Worktree isolation & Safety

Parallel autonomous agents editing the same repository checkout can cause dirty-state collisions. The mod supports fail-closed Git worktree isolation:

```text
project/
    main checkout

AI workspaces/
├── claude-auth/
├── claude-frontend/
└── codex-review/
```

- If worktree creation fails, isolation **fails closed** (reverting to shared workspace mode with explicit notification).
- Worktree diffs inspect uncommitted dirty files directly inside the worktree directory.
- Merging commits dirty worktree changes before applying to the main checkout.

---

# Security & Permissions

### Safe Defaults
By default, newly spawned agents start with restricted permissions:
- **Claude**: Defaults to `PLAN` mode (read-only reasoning).
- **Codex**: Defaults to `WORKSPACE-WRITE` mode.

Unrestricted autonomy (`BYPASS PERMISSIONS` / `DANGER FULL ACCESS` / `YOLO`) requires deliberate user configuration and is surfaced prominently in the HUD:
```text
⚠ BYPASS PERMISSIONS
```

### Local Shell Execution (`/ai shell <cmd>` or `!<cmd>`)
> ⚠️ **Important**: The mod includes a built-in shell runner (`/ai shell <command>` or `!<command>`) that executes commands locally on your machine via `cmd.exe /c` (Windows) or `bash -c` (Linux/macOS) in the focused agent's working directory. Use with appropriate care.

---

# Commands

Quick prompts:
```text
/claude <prompt>
/c <prompt>

/codex <prompt>
/x <prompt>
```

Multi-agent control:
```text
/ai spawn claude <label> [worktree|shared]
/ai spawn codex <label> [worktree|shared]
/ai focus <label>
/ai instances
/ai stop [all|<label>]
/ai merge <label>
```

Sessions & Screens:
```text
/ai composer
/ai transcript
/ai diff
/ai sessions
/ai tasks
/ai model
/ai permissions
/ai hud
```

Project management:
```text
/project <name>
/project add <name> <path>
/project list
```

---

# Default keybindings

| Key | Action |
|---|---|
| `U` | Open AI Composer |
| `K` | Interrupt focused agent |
| `P` | Cycle permission mode |
| `T` | Open Transcript Viewer |
| `H` | Cycle HUD density mode |
| `Ctrl+Tab` | Cycle focused agent |
| `Alt` | HUD detail modifier |

---

# Development stack

```text
Minecraft        26.1.2
Fabric Loader    0.19.3
Fabric API       0.155.2+26.1.2
Fabric Loom      1.17.19
Java             25
```

---

# Disclaimer

This mod communicates with local CLI instances of Claude Code and OpenAI Codex. Depending on user-configured permission modes, local agents and shell commands can modify files, execute subprocesses, and interact with your development environment. Use appropriate caution when granting write or unrestricted execution permissions.

This project is an independent open-source tool and is not affiliated with Mojang, Microsoft, Anthropic, or OpenAI.
