---
name: Operate Notebook config validation strategy
description: How widget configs are validated against V2 schemas and how the LLM is asked to self-correct
type: project
---

Widget configs from the LLM are validated before rendering, with a one-shot auto-retry on failure.

**Why:** Hallucinated columns/endpoints are the most likely demo failure mode. Validating with Zod and feeding the error back to the LLM gives the best correctness-per-effort. Stephan picked this over "render anyway with fallback."

**How to apply:**
- Maintain a registry `ENDPOINT_SCHEMAS: Record<endpoint, ZodSchema>` mapping each curated V2 endpoint to its response schema from `@camunda/camunda-api-zod-schemas`.
- On LLM response: parse JSON, then validate `columns[]` against the response schema and `endpoint` against the curated list.
- On validation failure: send one follow-up message to the LLM with the specific error and valid options ("column `X` not on `Y`; valid fields: [...]"). Render the corrected config.
- **Cap retries at 1.** Second failure → user-facing error: "Couldn't generate a valid widget — try rephrasing your prompt." No infinite loops.
- If a curated endpoint has no Zod schema available, **drop it from the curated list** rather than write new schemas. Hackday scope.
- System prompt should also include trimmed example *responses* per endpoint (not just requests) — validation is the safety net, prompt quality is the first line of defense.
- **Revisit trigger:** if retries push perceived latency past ~6s consistently in demos, fall back to "render with `—` for missing fields + soft warning above table" and skip the retry.
