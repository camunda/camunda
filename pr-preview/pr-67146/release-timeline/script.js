const timelineUrl = "./timeline.json";

const getValidatedQueryDate = () => {
  const value = new URLSearchParams(window.location.search).get("date");
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return null;
  }

  const [year, month, day] = value.split("-").map(Number);
  const utcDate = new Date(Date.UTC(year, month - 1, day, 12));
  if (
    Number.isNaN(utcDate.getTime()) ||
    utcDate.getUTCFullYear() !== year ||
    utcDate.getUTCMonth() !== month - 1 ||
    utcDate.getUTCDate() !== day
  ) {
    return null;
  }

  return new Date(`${value}T12:00:00`);
};

const today = getValidatedQueryDate() ?? new Date();
const todayLabel = new Intl.DateTimeFormat("en-US", {
  dateStyle: "medium",
}).format(today);

const todayEl = document.getElementById("today");
const currentPhaseEl = document.getElementById("current-phase");
const mergeTargetsEl = document.getElementById("merge-targets");
const togglePastBtn = document.getElementById("toggle-past");
const toggleFutureBtn = document.getElementById("toggle-future");
const timelineEl = document.getElementById("timeline");

let showPast = false;
let showFuture = false;

const isTodayInRange = (start, end) => {
  const startDate = new Date(`${start}T00:00:00`);
  const endDate = new Date(`${end}T23:59:59`);
  return today >= startDate && today <= endDate;
};

const isPastPhase = (end) => {
  const endDate = new Date(`${end}T23:59:59`);
  return endDate < today;
};

const isFarFuturePhase = (start) => {
  const startDate = new Date(`${start}T00:00:00`);
  const futureCutoff = new Date(today);
  futureCutoff.setDate(futureCutoff.getDate() + 21);
  return startDate > futureCutoff;
};

const formatRange = (start, end) => {
  const formatter = new Intl.DateTimeFormat("en-US", { dateStyle: "medium" });
  const startLabel = formatter.format(new Date(`${start}T00:00:00`));
  const endLabel = formatter.format(new Date(`${end}T00:00:00`));
  return `${startLabel} - ${endLabel}`;
};

const loadTimeline = async () => {
  const response = await fetch(timelineUrl);
  if (!response.ok) {
    throw new Error("Unable to load timeline.json");
  }
  const data = await response.json();
  return Array.isArray(data.timeline) ? data.timeline : [];
};

const buildChip = (text) => {
  const chip = document.createElement("span");
  chip.className = "chip";
  chip.textContent = text;
  return chip;
};

const buildBranchItem = (phase) => {
  const item = document.createElement("li");
  item.className = "branch-item";

  const branch = document.createElement("span");
  branch.className = "branch-pill";
  branch.textContent = phase.branch || "Unknown branch";

  const note = document.createElement("span");
  note.className = "branch-note";
  note.textContent = phase.note || "No note available.";

  item.append(branch, note);
  return item;
};

const buildLabeledValue = (label, value) => {
  const line = document.createElement("span");
  const labelEl = document.createElement("strong");
  labelEl.textContent = `${label}: `;
  const valueEl = document.createElement("span");
  valueEl.textContent = value;
  line.append(labelEl, valueEl);
  return line;
};

const renderMergeTargets = (phases) => {
  mergeTargetsEl.replaceChildren();
  mergeTargetsEl.appendChild(
    buildBranchItem({
      branch: "main",
      note: "Merge features for next minor version.",
    })
  );

  if (!phases || phases.length === 0) {
    return;
  }

  phases
    .filter((phase) => phase.branch !== "main")
    .forEach((phase) => {
      mergeTargetsEl.appendChild(buildBranchItem(phase));
    });
};

const renderTimeline = (timeline, activePhases) => {
  timelineEl.replaceChildren();
  timeline.forEach((phase, index) => {
    const item = document.createElement("li");
    item.className = "timeline-item fade-in";
    item.style.animationDelay = `${index * 80}ms`;

    if (isPastPhase(phase.end)) {
      item.dataset.past = "true";
      if (!showPast) {
        item.classList.add("is-hidden");
      }
    }

    if (isFarFuturePhase(phase.start)) {
      item.dataset.future = "true";
      if (!showFuture) {
        item.classList.add("is-hidden");
      }
    }

    if (activePhases && activePhases.some((active) => active.title === phase.title)) {
      item.classList.add("active");
    }

    const dot = document.createElement("span");
    dot.className = "timeline-dot";

    const card = document.createElement("div");
    card.className = "timeline-card";

    const title = document.createElement("h4");
    title.textContent = phase.title;

    const meta = document.createElement("div");
    meta.className = "timeline-meta";

    const date = buildLabeledValue("Dates", formatRange(phase.start, phase.end));
    const branches = buildLabeledValue("Branch", phase.branch);
    const note = buildLabeledValue("Note", phase.note);

    if (phase.description) {
      const description = buildLabeledValue("Details", phase.description);
      meta.append(date, branches, note, description);
    } else {
      meta.append(date, branches, note);
    }
    card.append(title, meta);
    item.append(dot, card);
    timelineEl.appendChild(item);
  });
};

const updatePastVisibility = () => {
  if (!togglePastBtn) {
    return;
  }

  const pastItems = timelineEl.querySelectorAll('[data-past="true"]');
  pastItems.forEach((item) => {
    item.classList.toggle("is-hidden", !showPast);
  });

  togglePastBtn.hidden = pastItems.length === 0;
  togglePastBtn.textContent = showPast ? "Hide past phases" : "Show past phases";
  togglePastBtn.setAttribute("aria-expanded", showPast ? "true" : "false");
};

const updateFutureVisibility = () => {
  if (!toggleFutureBtn) {
    return;
  }

  const futureItems = timelineEl.querySelectorAll('[data-future="true"]');
  futureItems.forEach((item) => {
    item.classList.toggle("is-hidden", !showFuture);
  });

  toggleFutureBtn.hidden = futureItems.length === 0;
  toggleFutureBtn.textContent = showFuture
    ? "Hide future phases"
    : "Show future phases";
  toggleFutureBtn.setAttribute("aria-expanded", showFuture ? "true" : "false");
};

const boot = async () => {
  todayEl.textContent = todayLabel;
  try {
    const timeline = await loadTimeline();
    const activePhases = timeline.filter((phase) =>
      isTodayInRange(phase.start, phase.end)
    );

    currentPhaseEl.replaceChildren();
    if (activePhases.length) {
      activePhases.forEach((phase) => {
        const line = document.createElement("span");
        line.className = "phase-line";
        line.textContent = phase.title;
        currentPhaseEl.appendChild(line);
      });
    } else {
      currentPhaseEl.textContent = "Between phases";
    }
    renderMergeTargets(activePhases);
    renderTimeline(timeline, activePhases);
    updatePastVisibility();
    updateFutureVisibility();

    if (togglePastBtn) {
      togglePastBtn.addEventListener("click", () => {
        showPast = !showPast;
        updatePastVisibility();
      });
    }

    if (toggleFutureBtn) {
      toggleFutureBtn.addEventListener("click", () => {
        showFuture = !showFuture;
        updateFutureVisibility();
      });
    }
  } catch (error) {
    currentPhaseEl.textContent = "Timeline unavailable";
    mergeTargetsEl.replaceChildren();
    mergeTargetsEl.appendChild(buildChip("Check timeline.json"));
  }
};

boot();
