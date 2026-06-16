# Agent Instructions

## Current Direction

StayWithMe should move toward a PlayerEngine-first architecture. Use Player2NPC and PlayerEngine as design references when they are relevant, especially around companion lifecycle, PlayerEngine task execution, inventory/interaction provider behavior, and conversation lifecycle.

Reference repositories:

- Player2NPC: https://github.com/Goodbird-git/Player2NPC
- PlayerEngine: https://github.com/Goodbird-git/PlayerEngine

This reference check is guidance, not a mandatory step before every edit. Do not expand unrelated login, password, or captcha flows. Prefer implementing Player2NPC-style companion behavior first, and keep StayWithMe focused on LLM planning, memory, expedition policy, vanilla-survival constraints, debug UI, and fallback behavior.
