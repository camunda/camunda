---
name: Operate Notebook persistence & routing
description: Multi-notebook localStorage with /notebooks list page and /notebooks/:id notebook view
type: project
---

Notebook persistence is **localStorage, multi-notebook, no list page**. Routes:
- `/notebooks` — **smart redirect** (NOT a page). If notebooks exist → redirect to most-recently-updated. If none exist → create a fresh notebook and redirect to its ID.
- `/notebooks/:id` — individual notebook (this is the only real page)
- Switching between notebooks: Carbon `<OverflowMenu>` or `<ComboBox>` switcher in the notebook header, with "New notebook" item at the bottom

**Why:** Stephan said "we don't need a landing page" — explicitly killed the list page UX. Smart redirect keeps the URL clean and gets users to value instantly. Multi-notebook capability stays accessible via header switcher without a dedicated page.

**How to apply:**
- Two-key storage design:
  - `operate.notebooks.v1.index` — `[{ id, title, updatedAt, widgetCount }, ...]`. Powers the list page in one read.
  - `operate.notebooks.v1.<id>` — actual notebook content. One key per notebook.
- Versioned keys for future schema breaks.
- On any change inside a notebook: update both the notebook key and the index entry. Debounced ~500ms.
- On load: validate with Zod; on failure show visible error toast and start blank. Do not silently swallow.
- **Naming:** new notebooks start as "Untitled notebook." After first prompt, auto-rename to a truncated version of the prompt (~50 chars). User can click-to-edit the title inline.
- **Notebook switcher** in the notebook header: shows other saved notebooks + "New notebook" + "Delete this notebook" actions. No dedicated list page.
- **Delete:** in the switcher overflow menu → confirm modal → remove from index + delete the notebook key. Confirm modal IS used for delete (destructive, localStorage-only, standard UX hygiene). This does NOT contradict the "no confirm modals on mutations" rule which was about V2 API mutations. After delete, redirect to most-recent remaining notebook (or create a fresh one if none left).
- **Future-proofing TODO:** "Notebook configs contain arbitrary endpoint/method/body. Before any share/export feature, treat configs as untrusted input and re-validate on load. Currently safe because configs only come from the user's own LLM session."
