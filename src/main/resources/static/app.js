const state = {
  city: "Chicago",
  spirit: "any",
  alcoholic: "any",
  recommendations: [],
  selected: null,
  saved: new Set(JSON.parse(localStorage.getItem("aeromix-saved") || "[]")),
  suggestionTimer: null,
  suggestionRequest: null,
  toastTimer: null
};

const elements = {
  form: document.querySelector("#weatherForm"),
  cityInput: document.querySelector("#cityInput"),
  suggestions: document.querySelector("#suggestions"),
  ribbon: document.querySelector("#weatherRibbon"),
  grid: document.querySelector("#cocktailGrid"),
  status: document.querySelector("#statusLine"),
  pairing: document.querySelector("#pairingNarrative"),
  spiritChoices: document.querySelector("#spiritChoices"),
  alcoholChoices: document.querySelector("#alcoholChoices"),
  savedCount: document.querySelector("#savedCount"),
  savedLink: document.querySelector("#savedLink"),
  refresh: document.querySelector("#refreshButton"),
  dialog: document.querySelector("#recipeDialog"),
  dialogClose: document.querySelector("#dialogClose"),
  dialogArt: document.querySelector("#dialogArt"),
  dialogScore: document.querySelector("#dialogScore"),
  dialogTitle: document.querySelector("#dialogTitle"),
  dialogSummary: document.querySelector("#dialogSummary"),
  dialogMeta: document.querySelector("#dialogMeta"),
  ingredientList: document.querySelector("#ingredientList"),
  dialogInstructions: document.querySelector("#dialogInstructions"),
  saveRecipe: document.querySelector("#saveRecipe"),
  toast: document.querySelector("#toast")
};

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function queryUrl() {
  const params = new URLSearchParams({ city: state.city, limit: "6" });
  if (state.spirit !== "any") params.set("spirit", state.spirit);
  if (state.alcoholic !== "any") params.set("alcoholic", state.alcoholic);
  return `/api/v1/recommendations?${params}`;
}

async function loadRecommendations({ scroll = false } = {}) {
  setLoading();
  try {
    const response = await fetch(queryUrl(), { headers: { Accept: "application/json" } });
    if (!response.ok) {
      const problem = await response.json().catch(() => ({}));
      throw new Error(problem.detail || "We couldn’t read that forecast.");
    }
    const data = await response.json();
    state.city = data.weather.city;
    state.recommendations = data.recommendations;
    renderWeather(data.weather);
    renderRecommendations(data.recommendations);
    elements.pairing.textContent = data.pairing.narrative;
    elements.status.textContent =
      `${data.recommendations.length} weather-paired pours · ${data.pairing.label}`;
    document.body.dataset.weather = data.pairing.group.toLowerCase();
    if (scroll) {
      document.querySelector("#recommendations").scrollIntoView({ behavior: "smooth" });
    }
  } catch (error) {
    renderError(error.message);
  }
}

function setLoading() {
  elements.grid.setAttribute("aria-busy", "true");
  elements.status.textContent = "Balancing the forecast with the bar…";
  elements.grid.innerHTML =
    '<div class="skeleton-card"></div><div class="skeleton-card"></div><div class="skeleton-card"></div>';
}

function renderWeather(weather) {
  const location = [weather.city, weather.region, weather.country].filter(Boolean).join(", ");
  const badge = weather.live
    ? '<em class="live-badge">Live</em>'
    : '<em class="live-badge fallback">Estimated</em>';
  elements.ribbon.innerHTML = `
    <div class="weather-data">
      <div class="weather-place">
        <strong><span class="weather-full">${escapeHtml(location)}</span><span class="weather-short">${escapeHtml(weather.city)}</span> ${badge}</strong>
        <span>${escapeHtml(weather.condition)}<span class="weather-mobile-detail">${weather.region ? ` · ${escapeHtml(weather.region)}` : ""}</span></span>
        <a class="weather-source" href="https://open-meteo.com/" target="_blank" rel="noopener noreferrer">Weather by Open-Meteo</a>
      </div>
      <div class="weather-metric">
        <strong>${Math.round(weather.temperatureC)}°</strong>
        <span>Temperature</span>
      </div>
      <div class="weather-metric">
        <strong>${Math.round(weather.relativeHumidity)}%</strong>
        <span>Humidity</span>
      </div>
      <div class="weather-metric">
        <strong>${Math.round(weather.windSpeedKph)}</strong>
        <span>km/h wind</span>
      </div>
    </div>`;
}

function renderRecommendations(recommendations) {
  elements.grid.setAttribute("aria-busy", "false");
  if (!recommendations.length) {
    elements.grid.innerHTML = `
      <div class="empty-state">
        <h3>No pours match that exact filter.</h3>
        <p>Try “Any” base or include both cocktail styles.</p>
      </div>`;
    return;
  }

  elements.grid.innerHTML = recommendations.map((cocktail) => `
    <article class="cocktail-card" data-slug="${escapeHtml(cocktail.slug)}">
      <div class="card-art art-${escapeHtml(cocktail.slug)}">
        <span class="match">${cocktail.matchScore}% match</span>
        <button class="favorite ${state.saved.has(cocktail.slug) ? "saved" : ""}"
                type="button" data-save="${escapeHtml(cocktail.slug)}"
                aria-label="${state.saved.has(cocktail.slug) ? "Remove" : "Save"} ${escapeHtml(cocktail.name)}">
          ${state.saved.has(cocktail.slug) ? "♥" : "♡"}
        </button>
        <div class="glass" aria-hidden="true"></div>
      </div>
      <div class="card-content">
        <p class="card-kicker">${cocktail.alcoholic ? escapeHtml(cocktail.spirit) : "zero-proof"} · ${escapeHtml(cocktail.glass)}</p>
        <h3>${escapeHtml(cocktail.name)}</h3>
        <p class="card-summary">${escapeHtml(cocktail.summary)}</p>
        <p class="reason">${escapeHtml(cocktail.reason)}</p>
        <div class="tags">
          ${cocktail.flavorTags.slice(0, 3).map((tag) => `<span class="tag">${escapeHtml(tag)}</span>`).join("")}
        </div>
        <button class="view-recipe" type="button" data-recipe="${escapeHtml(cocktail.slug)}">
          View the recipe <span aria-hidden="true">↗</span>
        </button>
      </div>
    </article>`).join("");
}

function renderError(message) {
  elements.grid.setAttribute("aria-busy", "false");
  elements.status.textContent = "The forecast could not be paired.";
  elements.grid.innerHTML = `
    <div class="error-state">
      <h3>Cloud cover in the connection.</h3>
      <p>${escapeHtml(message)}</p>
      <p>Try another city or refresh in a moment.</p>
    </div>`;
}

function openRecipe(slug) {
  const cocktail = state.recommendations.find((item) => item.slug === slug);
  if (!cocktail) return;
  state.selected = cocktail;
  elements.dialogArt.className = `dialog-art art-${cocktail.slug}`;
  elements.dialogScore.textContent = `${cocktail.matchScore}% match`;
  elements.dialogTitle.textContent = cocktail.name;
  elements.dialogSummary.textContent = cocktail.summary;
  elements.dialogMeta.innerHTML = `
    <span>${escapeHtml(cocktail.alcoholic ? cocktail.spirit : "zero-proof")}</span>
    <span>${escapeHtml(cocktail.glass)}</span>
    ${cocktail.flavorTags.slice(0, 2).map((tag) => `<span>${escapeHtml(tag)}</span>`).join("")}`;
  elements.ingredientList.innerHTML = cocktail.ingredients.map((ingredient) => `
    <li><span>${escapeHtml(ingredient.name)}</span><span>${escapeHtml(ingredient.amount)}</span></li>`).join("");
  elements.dialogInstructions.textContent = cocktail.instructions;
  updateDialogSaveButton();
  elements.dialog.showModal();
}

function toggleSaved(slug) {
  if (state.saved.has(slug)) {
    state.saved.delete(slug);
    showToast("Removed from your saved pours.");
  } else {
    state.saved.add(slug);
    showToast("Saved for later.");
  }
  localStorage.setItem("aeromix-saved", JSON.stringify([...state.saved]));
  updateSavedCount();
  renderRecommendations(state.recommendations);
  updateDialogSaveButton();
}

function updateSavedCount() {
  elements.savedCount.textContent = state.saved.size;
}

function updateDialogSaveButton() {
  if (!state.selected) return;
  elements.saveRecipe.textContent = state.saved.has(state.selected.slug)
    ? "Remove from saved pours"
    : "Save this pour";
}

function showToast(message) {
  elements.toast.textContent = message;
  elements.toast.classList.add("show");
  clearTimeout(state.toastTimer);
  state.toastTimer = setTimeout(() => elements.toast.classList.remove("show"), 2400);
}

async function searchLocations(query) {
  if (state.suggestionRequest) state.suggestionRequest.abort();
  if (query.trim().length < 3) {
    state.suggestionRequest = null;
    elements.suggestions.innerHTML = "";
    elements.cityInput.setAttribute("aria-expanded", "false");
    return;
  }
  const controller = new AbortController();
  state.suggestionRequest = controller;
  try {
    const response = await fetch(
      `/api/v1/locations?query=${encodeURIComponent(query.trim())}`,
      { signal: controller.signal }
    );
    if (!response.ok) return;
    const locations = await response.json();
    if (controller !== state.suggestionRequest) return;
    elements.suggestions.innerHTML = locations.map((location) => `
      <button class="suggestion" type="button" role="option" data-location="${escapeHtml(location.displayName)}">
        ${escapeHtml(location.displayName)}
      </button>`).join("");
    elements.cityInput.setAttribute("aria-expanded", String(locations.length > 0));
  } catch (error) {
    if (error.name === "AbortError") return;
    elements.suggestions.innerHTML = "";
    elements.cityInput.setAttribute("aria-expanded", "false");
  } finally {
    if (controller === state.suggestionRequest) state.suggestionRequest = null;
  }
}

function clearSuggestions() {
  clearTimeout(state.suggestionTimer);
  state.suggestionTimer = null;
  if (state.suggestionRequest) {
    state.suggestionRequest.abort();
    state.suggestionRequest = null;
  }
  elements.suggestions.innerHTML = "";
  elements.cityInput.setAttribute("aria-expanded", "false");
}

elements.form.addEventListener("submit", (event) => {
  event.preventDefault();
  const city = elements.cityInput.value.trim();
  if (city.length < 2) return;
  state.city = city;
  clearSuggestions();
  loadRecommendations({ scroll: true });
});

elements.cityInput.addEventListener("input", () => {
  clearTimeout(state.suggestionTimer);
  state.suggestionTimer = setTimeout(() => searchLocations(elements.cityInput.value), 280);
});

elements.suggestions.addEventListener("click", (event) => {
  const button = event.target.closest("[data-location]");
  if (!button) return;
  elements.cityInput.value = button.dataset.location;
  state.city = button.dataset.location;
  clearSuggestions();
  loadRecommendations({ scroll: true });
});

document.querySelector(".quick-cities").addEventListener("click", (event) => {
  const button = event.target.closest("[data-city]");
  if (!button) return;
  elements.cityInput.value = button.dataset.city;
  state.city = button.dataset.city;
  clearSuggestions();
  loadRecommendations({ scroll: true });
});

elements.spiritChoices.addEventListener("click", (event) => {
  const button = event.target.closest("[data-spirit]");
  if (!button) return;
  state.spirit = button.dataset.spirit;
  elements.spiritChoices.querySelectorAll(".choice").forEach((item) => {
    item.classList.remove("active");
    item.setAttribute("aria-pressed", "false");
  });
  button.classList.add("active");
  button.setAttribute("aria-pressed", "true");
  loadRecommendations();
});

elements.alcoholChoices.addEventListener("click", (event) => {
  const button = event.target.closest("[data-alcohol]");
  if (!button) return;
  state.alcoholic = button.dataset.alcohol;
  elements.alcoholChoices.querySelectorAll(".choice").forEach((item) => {
    item.classList.remove("active");
    item.setAttribute("aria-pressed", "false");
  });
  button.classList.add("active");
  button.setAttribute("aria-pressed", "true");
  loadRecommendations();
});

elements.grid.addEventListener("click", (event) => {
  const save = event.target.closest("[data-save]");
  if (save) {
    toggleSaved(save.dataset.save);
    return;
  }
  const recipe = event.target.closest("[data-recipe]");
  if (recipe) openRecipe(recipe.dataset.recipe);
});

elements.dialogClose.addEventListener("click", () => elements.dialog.close());
elements.dialog.addEventListener("click", (event) => {
  if (event.target === elements.dialog) elements.dialog.close();
});
elements.saveRecipe.addEventListener("click", () => {
  if (state.selected) toggleSaved(state.selected.slug);
});
elements.refresh.addEventListener("click", () => loadRecommendations({ scroll: true }));
elements.savedLink.addEventListener("click", () => {
  if (!state.saved.size) {
    showToast("Save a recipe with the heart button first.");
    return;
  }
  document.querySelector("#recommendations").scrollIntoView({ behavior: "smooth" });
  showToast(`${state.saved.size} saved pour${state.saved.size === 1 ? "" : "s"} on this device.`);
});

document.addEventListener("click", (event) => {
  if (!event.target.closest(".weather-search")) clearSuggestions();
});

updateSavedCount();
loadRecommendations();
