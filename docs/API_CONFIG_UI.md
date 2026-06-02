# API Configuration UI

StayWithMe uses one OpenAI-compatible API configuration for:

- natural-language task planning
- ore distribution advice
- mining expedition strategy
- bounded stuck-route construction advice

## Open The UI

While inside a world, use either:

```text
O
/staywithmeconfig
```

## API Tab

1. Choose a provider preset or enter a custom OpenAI-compatible base URL.
2. Enter the model name.
3. Enter an API key when configuring or replacing the stored key.
4. Select `Save`.
5. Select `Test Connection`.

The API key input is masked by default. `Show` only reveals the newly typed value. The server never sends the stored API key back to a client; the UI only reports whether a key is already configured.
The newly typed key is cleared only after the server confirms the save. `Test Connection` can be run before enabling LLM behavior, as long as a key has already been saved.

Select `Clear`, then `Save`, to remove the stored API key. Leaving the key field empty keeps the current stored key.

## Advanced Tab

The advanced tab contains experimental integration switches:

- `PlayerEngine`
- `SmartBrainLib`
- `Baritone`

These switches are not required for normal Forge-native behavior. Integration changes apply to newly spawned or reloaded companions.

## Multiplayer Permissions

Singleplayer owners can update the configuration directly. On a dedicated server, only operators with permission level 2 or higher can save or test server API settings.
