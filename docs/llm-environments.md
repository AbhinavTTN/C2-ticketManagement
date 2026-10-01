# Connecting the ask endpoint to a language model

`POST /api/ai/ask` calls a chat endpoint and reads the answer text, never a thinking trace. Test and production post to `{base-url}/chat/completions` and read `choices[0].message.content`. The dev profile sets `ollama-native: true`, which posts to Ollama `POST /api/chat` instead, because Ollama's `/v1/chat/completions` shim ignores `think` and can return an empty `content`. A bearer token is sent only on the OpenAI-compatible call, and only when `LLM_API_KEY` is set. The model name is never hardcoded in Java.

Select the environment with `SPRING_PROFILES_ACTIVE`.

| Profile | File | What it uses |
| --- | --- | --- |
| `dev` | `application-dev.yml` | Local Ollama, model `qwen3.5:4b`, unless overridden |
| `test` | `application-test.yml` | Only `LLM_BASE_URL`, `LLM_MODEL`, and `LLM_API_KEY` |
| `prod` | `application-prod.yml` | Only those same variables, from the secret store |

`LLM_THINK=false` is sent as `"think": false`. Use that for Qwen thinking models so the answer stays in `content`. Leave it unset for providers that do not understand the field.

## Development (this machine)

Ollama is already the development target.

1. Start Ollama and confirm the model is present:

   ```bash
   ollama serve
   ollama pull qwen3.5:4b
   curl -s http://127.0.0.1:11434/api/tags
   ```

2. Start the app with the dev profile and the local database:

   ```bash
   SPRING_PROFILES_ACTIVE=dev \
   SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/tickets \
   SPRING_DATASOURCE_USERNAME=tickets \
   SPRING_DATASOURCE_PASSWORD=tickets \
   mvn spring-boot:run
   ```

   That uses Ollama at `http://127.0.0.1:11434/api/chat` with `think: false` and model `qwen3.5:4b`. To try another local model without editing files:

   ```bash
   LLM_MODEL=llama3.1:8b SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
   ```

   Pull that tag with `ollama pull` first. `ollama list` shows what is already on disk.

## Test

Point test at a shared endpoint, not a developer laptop.

1. Choose a model the test host already serves. Examples that match this client:

   - Another Ollama host: `LLM_BASE_URL=http://ollama.test.internal:11434/v1`, `LLM_MODEL` set to a pulled tag, `LLM_API_KEY` empty, `LLM_THINK=false` for Qwen.
   - OpenAI: `LLM_BASE_URL=https://api.openai.com/v1`, `LLM_MODEL` set to the test model name, `LLM_API_KEY` set to the test key.
   - Any gateway that accepts `POST {base-url}/chat/completions` with a JSON `model` and `messages` body.

2. Export the variables in the test runner or deployment, then start with `SPRING_PROFILES_ACTIVE=test`. If `LLM_BASE_URL` or `LLM_MODEL` is empty, `/api/ai/ask` returns 503 `LANGUAGE_MODEL_NOT_CONFIGURED` instead of calling a default host.

3. Ask one question that should cite a known ticket and one that should not. Confirm the answer text comes from `content`, not from a thinking trace.

## Production

1. Store `LLM_BASE_URL`, `LLM_MODEL`, and `LLM_API_KEY` in the production secret store. Do not commit them and do not reuse the test key.
2. Start with `SPRING_PROFILES_ACTIVE=prod`. The production profile has no localhost default.
3. Confirm the gateway path is exactly `{LLM_BASE_URL}/chat/completions` and that it returns `choices[0].message.content`.
4. Changing the chat model does not require a database migration. Changing the embedding model does, because `chunk_records.embedding` is a fixed-width `vector` column.
