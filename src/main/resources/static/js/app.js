const state = {
    chartInstances: {},
    options: null,
    predictionOptions: null,
    currentUsername: null,
    currentUserEmail: null,
    currentNotificationEmail: null,
    currentPage: "overview",
    authMode: "login",
    theme: "light",
    latestVisuals: {
        timeline: [],
        histogram: [],
        locationBreakdown: [],
        categoryBreakdown: [],
        heatmap: []
    },
    visualizationFrame: null,
    lastFocusedElement: null
};

const daysOfWeek = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
const THEME_STORAGE_KEY = "fraudDetectionTheme";
const pageConfig = {
    overview: {
        label: "Overview workspace",
        heroKicker: "Customer-centric fraud monitoring",
        heroHeadline: "Track anomalies, behavior shifts, and live fraud risk without leaving the dashboard.",
        heroSummary: "Use the overview page to monitor KPI movement, review the live verdict, and confirm supporting behavior signals.",
        navSummary: "Review KPI movement, behavior signals, and the current fraud verdict before drilling deeper."
    },
    visualizations: {
        label: "Visualizations workspace",
        heroKicker: "Pattern exploration",
        heroHeadline: "Read timeline shifts, distribution changes, location spread, merchant mix, and activity density in one dedicated page.",
        heroSummary: "All visualizations stay tied to the same filter context, so chart-driven analysis never breaks the active customer story.",
        navSummary: "Use this page for time-series, histogram, location, merchant, and heatmap analysis."
    },
    investigation: {
        label: "Investigation workspace",
        heroKicker: "Evidence and model review",
        heroHeadline: "Move from suspicious transactions to live fraud scoring in a focused investigation workflow.",
        heroSummary: "Use the evidence table and prediction form together when validating flagged activity or simulating a new transaction.",
        navSummary: "Review recent evidence and run prediction checks without the chart-heavy layout."
    }
};
const chartThemePalettes = {
    light: {
        primary: "#148cff",
        secondary: "#00c2a8",
        accent: "#4fe3ff",
        danger: "#ff5f7a",
        ink: "#17324d",
        grid: "rgba(23, 50, 77, 0.1)",
        pointBorder: "#f5fbff",
        bars: ["#148cff", "#25a4ff", "#35bbff", "#47d0ff", "#50dfeb", "#55e4c9", "#71e4aa", "#9fe36c"],
        donut: ["#148cff", "#00c2a8", "#4fe3ff", "#ff5f7a", "#336fdd", "#8adfff", "#bff3dc"]
    },
    dark: {
        primary: "#49a8ff",
        secondary: "#3ce0b8",
        accent: "#7df0ff",
        danger: "#ff8099",
        ink: "#e4eef7",
        grid: "rgba(228, 238, 247, 0.12)",
        pointBorder: "#07111c",
        bars: ["#49a8ff", "#5bb7ff", "#71c7ff", "#86d9ff", "#7df0ff", "#55e4c9", "#6de0a3", "#a7e07a"],
        donut: ["#49a8ff", "#3ce0b8", "#7df0ff", "#ff8099", "#5e7cff", "#86d9ff", "#c1fff2"]
    }
};
const chartPalette = { ...chartThemePalettes.light };

document.addEventListener("DOMContentLoaded", () => {
    initializeTheme();
    bindEvents();
    bootstrap();
});

function bindEvents() {
    document.getElementById("themeToggle").addEventListener("click", handleThemeToggle);
    document.getElementById("loginForm").addEventListener("submit", handleLogin);
    document.getElementById("signupForm").addEventListener("submit", handleSignup);
    document.getElementById("notificationEmailForm").addEventListener("submit", handleNotificationEmailSubmit);
    document.getElementById("logoutButton").addEventListener("click", handleLogout);
    document.getElementById("filterForm").addEventListener("submit", handleFilterSubmit);
    document.getElementById("resetFilters").addEventListener("click", resetFilters);
    document.getElementById("predictionForm").addEventListener("submit", handlePredictionSubmit);
    document.getElementById("fraudAlertCloseButton").addEventListener("click", closeFraudAlertPopup);
    document.getElementById("fraudAlertModal").addEventListener("click", handleFraudAlertBackdropClick);
    document.querySelectorAll("[data-auth-mode]").forEach((button) => button.addEventListener("click", handleAuthModeButtonClick));
    document.querySelectorAll(".page-nav-btn").forEach((button) => button.addEventListener("click", handlePageButtonClick));
    window.addEventListener("hashchange", handleHashChange);
    document.addEventListener("keydown", handleFraudAlertKeydown);
}

function initializeTheme() {
    applyTheme(resolveStoredTheme(), { persist: false, rerenderCharts: false });
}

function handleThemeToggle() {
    const nextTheme = state.theme === "dark" ? "light" : "dark";
    applyTheme(nextTheme);
    showToast(`${capitalize(nextTheme)} theme enabled.`);
}

function applyTheme(theme, options = {}) {
    const { persist = true, rerenderCharts = true } = options;
    const nextTheme = theme === "dark" ? "dark" : "light";

    state.theme = nextTheme;
    document.documentElement.dataset.theme = nextTheme;
    document.documentElement.style.colorScheme = nextTheme;
    syncChartPalette(nextTheme);
    renderThemeToggle();

    if (persist) {
        writeStoredTheme(nextTheme);
    }

    if (rerenderCharts && state.currentPage === "visualizations") {
        renderVisualizationPage(state.latestVisuals);
    }
}

function resolveStoredTheme() {
    try {
        const storedTheme = window.localStorage.getItem(THEME_STORAGE_KEY);
        if (storedTheme === "dark" || storedTheme === "light") {
            return storedTheme;
        }
    } catch (error) {
        return document.documentElement.dataset.theme || "light";
    }

    const currentTheme = document.documentElement.dataset.theme;
    return currentTheme === "dark" || currentTheme === "light" ? currentTheme : "light";
}

function writeStoredTheme(theme) {
    try {
        window.localStorage.setItem(THEME_STORAGE_KEY, theme);
    } catch (error) {
        // Ignore storage write failures and keep the in-memory preference.
    }
}

function syncChartPalette(theme) {
    Object.assign(chartPalette, chartThemePalettes[theme === "dark" ? "dark" : "light"]);
}

function renderThemeToggle() {
    const button = document.getElementById("themeToggle");
    if (!button) {
        return;
    }

    const currentLabel = capitalize(state.theme);
    const nextLabel = state.theme === "dark" ? "light" : "dark";

    button.setAttribute("aria-pressed", String(state.theme === "dark"));
    button.setAttribute("aria-label", `Switch to ${nextLabel} theme`);
    setText("themeToggleCurrent", currentLabel);
    setText("themeToggleLabel", `Switch to ${nextLabel}`);
}

async function bootstrap() {
    try {
        const session = await api("/api/auth/session");
        if (session?.authenticated) {
            syncSessionState(session);
            await enterDashboard();
            return;
        }
    } catch (error) {
        showToast(error.message || "Unable to restore session.");
    }

    showLogin();
}

async function enterDashboard() {
    showDashboard();
    setActivePage(resolvePageFromHash());
    renderSessionDetails();
    await Promise.all([loadOptions(), loadPredictionForm()]);
    await refreshDashboard();
}

function showLogin() {
    setAuthMode("login");
    document.getElementById("loginView").hidden = false;
    document.getElementById("dashboardView").hidden = true;
    document.title = "Fraud Detection in Online Transaction";
}

function showDashboard() {
    document.getElementById("loginView").hidden = true;
    document.getElementById("dashboardView").hidden = false;
}

async function handleLogin(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const payload = {
        username: form.username.value.trim(),
        password: form.password.value
    };
    const errorNode = document.getElementById("loginError");
    errorNode.hidden = true;

    try {
        const session = await api("/api/auth/login", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        syncSessionState(session, {
            username: payload.username
        });
        form.reset();
        await enterDashboard();
        showToast("Signed in successfully.");
    } catch (error) {
        errorNode.textContent = error.message || "Authentication failed.";
        errorNode.hidden = false;
    }
}

async function handleSignup(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const notificationEmail = form.notificationEmail.value.trim();
    const payload = {
        username: form.username.value.trim(),
        email: form.email.value.trim(),
        password: form.password.value,
        confirmPassword: form.confirmPassword.value
    };
    if (notificationEmail) {
        payload.notificationEmail = notificationEmail;
    }
    const errorNode = document.getElementById("signupError");
    errorNode.hidden = true;

    try {
        const session = await api("/api/auth/signup", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        syncSessionState(session, {
            username: payload.username,
            email: payload.email,
            notificationEmail: notificationEmail || payload.email
        });
        form.reset();
        await enterDashboard();
        showToast("Account created and signed in.");
    } catch (error) {
        errorNode.textContent = error.message || "Account creation failed.";
        errorNode.hidden = false;
    }
}

async function handleNotificationEmailSubmit(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const feedbackNode = document.getElementById("notificationEmailFeedback");
    feedbackNode.hidden = true;

    const payload = {
        notificationEmail: form.notificationEmail.value.trim()
    };

    try {
        const session = await api("/api/auth/profile", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        syncSessionState(session);
        renderSessionDetails();
        feedbackNode.classList.remove("is-error");
        feedbackNode.textContent = "Notification email updated.";
        feedbackNode.hidden = false;
        showToast(`Fraud alerts will now be sent to ${state.currentNotificationEmail}.`);
    } catch (error) {
        feedbackNode.classList.add("is-error");
        feedbackNode.textContent = error.message || "Notification email update failed.";
        feedbackNode.hidden = false;
    }
}

async function handleLogout() {
    try {
        await api("/api/auth/logout", { method: "POST" });
    } catch (error) {
        showToast(error.message || "Logout request failed.");
    }
    state.currentUsername = null;
    state.currentUserEmail = null;
    state.currentNotificationEmail = null;
    state.currentPage = "overview";
    state.authMode = "login";
    state.latestVisuals = {
        timeline: [],
        histogram: [],
        locationBreakdown: [],
        categoryBreakdown: [],
        heatmap: []
    };
    if (window.location.hash) {
        window.history.replaceState(null, "", `${window.location.pathname}${window.location.search}`);
    }
    destroyCharts();
    closeFraudAlertPopup();
    applyPageCopy(pageConfig.overview);
    showLogin();
}

function syncSessionState(session, fallback = {}) {
    state.currentUsername = session?.username || fallback.username || "Operator";
    state.currentUserEmail = session?.email || fallback.email || null;
    state.currentNotificationEmail = session?.notificationEmail || fallback.notificationEmail || state.currentUserEmail || null;
}

function renderSessionDetails() {
    setText("sessionUser", state.currentUsername || "Operator");
    setText("profileUsername", state.currentUsername || "-");
    setText("profileAccountEmail", state.currentUserEmail || "-");
    setText("profileNotificationEmail", state.currentNotificationEmail || state.currentUserEmail || "-");

    const notificationInput = document.getElementById("notificationEmailInput");
    if (notificationInput && document.activeElement !== notificationInput) {
        notificationInput.value = state.currentNotificationEmail || state.currentUserEmail || "";
    }
}

function handleAuthModeButtonClick(event) {
    setAuthMode(event.currentTarget.dataset.authMode || "login");
}

function handlePageButtonClick(event) {
    const nextPage = event.currentTarget.dataset.page || "overview";
    if (resolvePageFromHash() !== nextPage) {
        window.location.hash = nextPage;
        return;
    }
    setActivePage(nextPage);
}

function setAuthMode(mode) {
    const nextMode = mode === "signup" ? "signup" : "login";
    state.authMode = nextMode;

    const loginVisible = nextMode === "login";
    document.getElementById("loginForm").hidden = !loginVisible;
    document.getElementById("signupForm").hidden = loginVisible;
    document.getElementById("loginError").hidden = true;
    document.getElementById("signupError").hidden = true;

    document.querySelectorAll("[data-auth-mode]").forEach((button) => {
        const active = button.dataset.authMode === nextMode;
        button.classList.toggle("is-active", active);
        button.setAttribute("aria-selected", String(active));
    });
}

function handleHashChange() {
    if (document.getElementById("dashboardView").hidden) {
        return;
    }
    setActivePage(resolvePageFromHash());
}

function resolvePageFromHash() {
    const nextPage = window.location.hash.replace(/^#/, "").trim().toLowerCase();
    return Object.prototype.hasOwnProperty.call(pageConfig, nextPage) ? nextPage : "overview";
}

function setActivePage(page) {
    const nextPage = Object.prototype.hasOwnProperty.call(pageConfig, page) ? page : "overview";
    state.currentPage = nextPage;

    Object.keys(pageConfig).forEach((key) => {
        const panel = document.getElementById(`${key}Page`);
        if (panel) {
            panel.hidden = key !== nextPage;
        }
    });

    document.querySelectorAll(".page-nav-btn").forEach((button) => {
        const active = button.dataset.page === nextPage;
        button.classList.toggle("is-active", active);
        button.setAttribute("aria-selected", String(active));
        button.tabIndex = active ? 0 : -1;
    });

    applyPageCopy(pageConfig[nextPage]);
    syncVisualizationPage();
}

function applyPageCopy(config) {
    setText("heroKicker", config.heroKicker);
    setText("heroHeadline", config.heroHeadline);
    setText("heroSummary", config.heroSummary);
    setText("pageNavTitle", config.label);
    setText("pageNavSummary", config.navSummary);
    document.title = `Fraud Detection in Online Transaction · ${config.label}`;
}

async function loadOptions() {
    const payload = await api("/api/dashboard/options");
    state.options = payload || {};
    renderFilters(payload || {});
}

async function loadPredictionForm() {
    try {
        state.predictionOptions = await api("/api/dashboard/prediction-form");
    } catch (error) {
        state.predictionOptions = {};
        showToast(error.message || "Prediction options could not be loaded.");
    }
    renderPredictionOptions(state.predictionOptions || {});
}

function renderFilters(payload) {
    const customerIds = asArray(anyOf(payload, ["customerIds", "customers", "customer_id_options"]));
    const transactionTypes = prependAll(asArray(anyOf(payload, ["transactionTypes", "types", "transaction_type_options"])));
    const locations = prependAll(asArray(anyOf(payload, ["locations", "locationOptions"])));
    const devices = prependAll(asArray(anyOf(payload, ["devices", "deviceOptions"])));
    const dateRange = anyOf(payload, ["dateRange", "range", "date_range"], {});

    fillSelect("customerId", customerIds, customerIds[0] || "");
    fillSelect("transactionType", transactionTypes, "All");
    fillSelect("location", locations, "All");
    fillSelect("device", devices, "All");

    const minDate = normalizeDate(anyOf(dateRange, ["start", "min", "from"], anyOf(payload, ["minDate", "startDate"])));
    const maxDate = normalizeDate(anyOf(dateRange, ["end", "max", "to"], anyOf(payload, ["maxDate", "endDate"])));
    document.getElementById("startDate").value = minDate || "";
    document.getElementById("endDate").value = maxDate || "";
    document.getElementById("datasetRange").textContent = minDate && maxDate ? `${minDate} to ${maxDate}` : "Profile window ready";
    document.getElementById("selectedCustomerBadge").textContent = customerIds[0] || "-";
}

function renderPredictionOptions(payload) {
    const transactionTypes = asArray(anyOf(payload, ["transactionTypes", "types"])).length
        ? asArray(anyOf(payload, ["transactionTypes", "types"]))
        : ["Debit", "Credit"];
    const channels = asArray(anyOf(payload, ["channels", "channelOptions"])).length
        ? asArray(anyOf(payload, ["channels", "channelOptions"]))
        : ["ATM", "Online", "Branch"];
    const occupations = asArray(anyOf(payload, ["occupations", "customerOccupations", "occupationOptions"])).length
        ? asArray(anyOf(payload, ["occupations", "customerOccupations", "occupationOptions"]))
        : ["Doctor", "Engineer", "Student", "Salaried"];

    fillSelect("predictionTransactionType", transactionTypes, transactionTypes[0] || "");
    fillSelect("predictionChannel", channels, channels[0] || "");
    fillSelect("predictionOccupation", occupations, occupations[0] || "");
}

async function handleFilterSubmit(event) {
    event.preventDefault();
    await refreshDashboard();
}

function resetFilters() {
    if (!state.options) {
        return;
    }
    renderFilters(state.options);
    refreshDashboard().catch((error) => showToast(error.message || "Unable to reload dashboard."));
}

async function refreshDashboard() {
    const query = new URLSearchParams(currentFilterState());
    document.getElementById("selectedCustomerBadge").textContent = query.get("customerId") || "-";
    const [overviewPayload, transactionsPayload] = await Promise.all([
        api(`/api/dashboard/overview?${query.toString()}`),
        api(`/api/dashboard/transactions?${query.toString()}&limit=12`)
    ]);

    renderOverview(overviewPayload || {});
    renderTransactions(transactionsPayload || []);
}

function currentFilterState() {
    return {
        customerId: document.getElementById("customerId").value,
        startDate: document.getElementById("startDate").value,
        endDate: document.getElementById("endDate").value,
        transactionType: document.getElementById("transactionType").value,
        location: document.getElementById("location").value,
        device: document.getElementById("device").value
    };
}

function renderOverview(payload) {
    const metrics = anyOf(payload, ["metrics"], {});
    const behavior = anyOf(payload, ["behavior", "riskAssessment", "behaviorSignals"], payload);
    const charts = anyOf(payload, ["charts", "visuals"], payload);
    const verdict = anyOf(behavior, ["verdict", "riskVerdict"], anyOf(payload, ["verdict"], {}));

    setText("metricTotalTransactions", formatInteger(anyOf(metrics, ["totalTransactions", "totalTx", "total_tx"], 0)));
    setText("metricTotalDebit", formatCurrency(anyOf(metrics, ["totalDebit", "total_debit"], 0)));
    setText("metricTotalCredit", formatCurrency(anyOf(metrics, ["totalCredit", "total_credit"], 0)));
    setText("metricAverageAmount", formatCurrency(anyOf(metrics, ["averageAmount", "avgAmount", "avg_amt"], 0)));
    setText("metricMaxTransaction", formatCurrency(anyOf(metrics, ["maxTransaction", "maxAmount", "max_amt"], 0)));
    setText("metricSuspiciousCount", formatInteger(anyOf(metrics, ["suspiciousCount", "flaggedCount", "suspicious_count"], 0)));
    setText("metricSuspiciousPercentage", formatPercent(anyOf(metrics, ["suspiciousPercentage", "suspiciousPct", "suspicious_pct"], 0)));
    setText("metricPrimaryDevice", anyOf(behavior, ["primaryDevice", "device"], anyOf(payload, ["primaryDevice"], "N/A")));
    setText("metricPrimaryLocation", anyOf(behavior, ["primaryLocation", "location"], anyOf(payload, ["primaryLocation"], "N/A")));
    setText(
        "metricVarianceAlert",
        anyOf(behavior, ["varianceAlert", "variance", "highVariance"], anyOf(payload, ["highVariance"], false)) ? "High variance" : "Stable"
    );

    const riskScore = anyOf(behavior, ["riskScore", "score"], anyOf(payload, ["riskScore"], anyOf(verdict, ["score"], 0)));
    setText("metricRiskScore", `${Math.round(Number(riskScore) || 0)} / 100`);
    setText(
        "metricWindowLabel",
        `${document.getElementById("startDate").value || "-"} to ${document.getElementById("endDate").value || "-"}`
    );

    const verdictLabel = anyOf(verdict, ["label", "title"], "No verdict");
    const verdictLevel = normalizeRiskLevel(anyOf(verdict, ["level", "severity", "status"], "neutral"));
    setText("riskVerdictLabel", verdictLabel);
    setText(
        "riskVerdictSummary",
        anyOf(
            verdict,
            ["summary", "message"],
            asArray(anyOf(behavior, ["indicators", "alerts", "factors"], anyOf(payload, ["indicators"], [])))[0] || "Behavior signals are being evaluated."
        )
    );
    renderVerdictBadge(verdictLevel, verdictLabel);
    renderIndicators(asArray(anyOf(behavior, ["indicators", "alerts", "factors"], anyOf(payload, ["indicators"], []))));

    const timeline = asArray(anyOf(charts, ["timeline", "timeSeries", "transactionsTimeline"]));
    const histogram = asArray(anyOf(charts, ["amountDistribution", "histogram", "distribution"]));
    const locationBreakdown = asArray(anyOf(charts, ["locationBreakdown", "locations"]));
    const categoryBreakdown = asArray(anyOf(charts, ["merchantCategorySplit", "merchantCategories", "categories"]));
    const heatmap = anyOf(charts, ["weekdayHourHeatmap", "heatmap"], []);

    state.latestVisuals = {
        timeline,
        histogram,
        locationBreakdown,
        categoryBreakdown,
        heatmap
    };
	// 🚨 Account Takeover Alert
	const alertBox = document.getElementById("takeoverAlertBox");

	if (payload.takeoverAlert) {
	    if (alertBox) {
	        alertBox.style.display = "block";
	        alertBox.textContent = payload.takeoverAlert;
	    }
	    showToast(payload.takeoverAlert);
	} else {
	    if (alertBox) {
	        alertBox.style.display = "none";
	    }
	}
    syncVisualizationPage();
}

function renderVerdictBadge(level, label) {
    const badge = document.getElementById("riskVerdictBadge");
    badge.className = `risk-badge risk-${level}`;
    badge.textContent = label;
}

function renderIndicators(items) {
    const list = document.getElementById("riskIndicators");
    list.innerHTML = "";

    if (!items.length) {
        const item = document.createElement("li");
        item.textContent = "No active behavioral alerts in the selected window.";
        list.appendChild(item);
        return;
    }

    items.forEach((entry) => {
        const item = document.createElement("li");
        item.textContent = typeof entry === "string" ? entry : anyOf(entry, ["message", "label"], "Behavior signal");
        list.appendChild(item);
    });
}

function renderTransactions(payload) {
    const rows = Array.isArray(payload) ? payload : asArray(anyOf(payload, ["transactions", "items", "data"]));
    const body = document.getElementById("transactionsTable");
    body.innerHTML = "";

    if (!rows.length) {
        const row = document.createElement("tr");
        row.innerHTML = `<td colspan="7">No transactions found for the selected filters.</td>`;
        body.appendChild(row);
        return;
    }

    rows.forEach((entry) => {
        const suspicious = Boolean(anyOf(entry, ["suspicious", "isSuspicious", "is_suspicious"], false));
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>${escapeHtml(anyOf(entry, ["transactionId", "transaction_id"], "-"))}</td>
            <td>${escapeHtml(formatDateTime(anyOf(entry, ["timestamp", "dateTime", "transactionDate"], "")))}</td>
            <td>${escapeHtml(formatCurrency(anyOf(entry, ["amount"], 0)))}</td>
            <td>${escapeHtml(anyOf(entry, ["transactionType", "transaction_type"], "-"))}</td>
            <td>${escapeHtml(anyOf(entry, ["location"], "-"))}</td>
            <td>${escapeHtml(anyOf(entry, ["device", "channel"], "-"))}</td>
            <td><span class="status-chip ${suspicious ? "flagged" : "clear"}">${suspicious ? "Flagged" : "Clear"}</span></td>
        `;
        body.appendChild(row);
    });
}

async function handlePredictionSubmit(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const payload = {
        transactionAmount: Number(form.amount.value),
        transactionType: form.transactionType.value,
        channel: form.channel.value,
        customerAge: Number(form.customerAge.value),
        customerOccupation: form.customerOccupation.value,
        transactionDuration: Number(form.transactionDuration.value),
        loginAttempts: Number(form.loginAttempts.value),
        accountBalance: Number(form.accountBalance.value)
    };

    try {
        const response = await api("/api/dashboard/predict", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        renderPrediction(response || {});
    } catch (error) {
        showToast(error.message || "Prediction failed.");
    }
}

function renderPrediction(payload) {
    const probability = clamp01(anyOf(payload, ["fraudProbability", "probability", "scoreProbability"], 0));
    const riskScore = Number(anyOf(payload, ["riskScore", "score"], probability * 100));
    const status = String(anyOf(payload, ["status", "prediction", "label", "verdict"], anyOf(payload, ["fraud"], false) ? "Fraud detected" : "Legitimate transaction")).toLowerCase();
    const isFraudPrediction = Boolean(anyOf(payload, ["fraud"], status.includes("fraud") || riskScore >= 60));
    const factors = asArray(anyOf(payload, ["factors", "reasons", "signals"]));

    const result = document.getElementById("predictionResult");
    result.className = `prediction-result ${isFraudPrediction ? "prediction-risky" : "prediction-safe"}`;

    document.getElementById("predictionMeterFill").style.width = `${Math.max(0, Math.min(100, riskScore))}%`;
    setText(
        "predictionLabel",
        anyOf(payload, ["label", "prediction", "verdict"], isFraudPrediction ? "Fraud detected" : "Legitimate transaction")
    );
    setText(
        "predictionProbability",
        `${formatPercent(probability * 100)} fraud probability • ${Math.round(riskScore)} / 100 risk score`
    );

    const list = document.getElementById("predictionFactors");
    list.innerHTML = "";
    if (!factors.length) {
        const item = document.createElement("li");
        item.textContent = "No additional model factors were returned.";
        list.appendChild(item);
    } else {
        factors.forEach((factor) => {
            const item = document.createElement("li");
            item.textContent = typeof factor === "string" ? factor : anyOf(factor, ["message", "label"], "Model factor");
            list.appendChild(item);
        });
    }

    if (anyOf(payload, ["notificationAttempted"], false)) {
        showToast(
            anyOf(
                payload,
                ["notificationMessage"],
                anyOf(payload, ["notificationSent"], false)
                    ? "Fraud alert email sent."
                    : "Fraud detected, but the alert email could not be sent."
            )
        );
    }

    if (isFraudPrediction) {
        showFraudAlertPopup(payload, { probability, riskScore, factors });
        return;
    }

    closeFraudAlertPopup();
}

function renderTimelineChart(timeline) {
    const points = timeline
        .map((entry) => {
            const rawTimestamp = anyOf(entry, ["timestamp", "time", "label"]);
            const parsedTimestamp = parseApiDate(rawTimestamp);
            return {
                rawTimestamp,
                parsedTimestamp,
                amount: Number(anyOf(entry, ["amount", "value", "count"], 0)),
                suspicious: Boolean(anyOf(entry, ["suspicious", "isSuspicious", "is_suspicious"], false))
            };
        })
        .filter((point) => point.rawTimestamp && point.parsedTimestamp && !Number.isNaN(point.parsedTimestamp.getTime()))
        .sort((left, right) => left.parsedTimestamp - right.parsedTimestamp);

    const labels = points.map((point) => formatTimelineLabel(point.parsedTimestamp));

    const amounts = points.map((point) => point.amount);
    const pointColors = points.map((point) => (point.suspicious ? chartPalette.danger : chartPalette.primary));
    const pointBorderWidths = points.map((point) => (point.suspicious ? 3 : 1.5));
    const minAmount = amounts.length ? Math.min(...amounts) : 0;
    const maxAmount = amounts.length ? Math.max(...amounts) : 0;
    const span = Math.max(1, maxAmount - minAmount);
    const suggestedMin = Math.max(0, minAmount - span * 0.18);
    const suggestedMax = maxAmount + span * 0.2;
    const timelineFill = buildAreaGradient("timelineChart", "rgba(20, 140, 255, 0.34)", "rgba(20, 140, 255, 0.03)");

    upsertChart("timelineChart", {
        type: "line",
        data: {
            labels,
            datasets: [
                {
                    label: "Transaction amount",
                    data: amounts,
                    borderColor: chartPalette.primary,
                    backgroundColor: timelineFill,
                    borderWidth: 3,
                    fill: true,
                    tension: 0.32,
                    cubicInterpolationMode: "monotone",
                    pointRadius: points.length > 60 ? 0 : 4,
                    pointHoverRadius: 5,
                    pointBackgroundColor: pointColors,
                    pointBorderColor: chartPalette.pointBorder,
                    pointBorderWidth: pointBorderWidths
                }
            ]
        },
        options: baseChartOptions({
            interaction: {
                mode: "index",
                intersect: false
            },
            scales: {
                x: {
                    ticks: {
                        color: chartPalette.ink,
                        maxRotation: 0,
                        autoSkip: true,
                        maxTicksLimit: 8
                    },
                    grid: { display: false }
                },
                y: {
                    suggestedMin,
                    suggestedMax,
                    ticks: {
                        color: chartPalette.ink,
                        callback: (value) => formatCurrency(value)
                    },
                    grid: { color: chartPalette.grid }
                }
            },
            plugins: {
                legend: {
                    display: false
                },
                tooltip: {
                    backgroundColor: tooltipBackground(),
                    padding: 12,
                    cornerRadius: 14,
                    callbacks: {
                        title: (items) => points[items[0].dataIndex]?.rawTimestamp || items[0].label,
                        label: (context) => formatCurrency(context.parsed.y)
                    }
                }
            }
        })
    });
}

function renderHistogramChart(histogram, timeline) {
    const bins = histogram.length ? normalizeDistribution(histogram) : buildHistogramFromTimeline(timeline);
    upsertChart("amountChart", {
        type: "bar",
        data: {
            labels: bins.map((entry) => entry.label),
            datasets: [
                {
                    label: "Transactions",
                    data: bins.map((entry) => entry.value),
                    backgroundColor: chartPalette.bars,
                    borderRadius: 10,
                    borderSkipped: false
                }
            ]
        },
        options: baseChartOptions({
            scales: {
                x: { ticks: { color: chartPalette.ink }, grid: { display: false } },
                y: { ticks: { color: chartPalette.ink }, grid: { color: chartPalette.grid } }
            }
        })
    });
}

function renderLocationChart(entries) {
    const series = normalizeBreakdown(entries);
    upsertChart("locationChart", {
        type: "bar",
        data: {
            labels: series.map((entry) => entry.label),
            datasets: [
                {
                    label: "Transactions",
                    data: series.map((entry) => entry.value),
                    backgroundColor: chartPalette.secondary,
                    borderRadius: 10
                }
            ]
        },
        options: baseChartOptions({
            indexAxis: "y",
            scales: {
                x: { ticks: { color: chartPalette.ink }, grid: { color: chartPalette.grid } },
                y: { ticks: { color: chartPalette.ink }, grid: { display: false } }
            }
        })
    });
}

function renderCategoryChart(entries) {
    const series = normalizeBreakdown(entries);
    upsertChart("categoryChart", {
        type: "doughnut",
        data: {
            labels: series.map((entry) => entry.label),
            datasets: [
                {
                    data: series.map((entry) => entry.value),
                    backgroundColor: chartPalette.donut,
                    hoverOffset: 10
                }
            ]
        },
        options: baseChartOptions({
            cutout: "62%"
        })
    });
}

function renderHeatmap(heatmap, timeline) {
    const root = document.getElementById("heatmap");
    root.innerHTML = "";

    const matrix = normalizeHeatmap(heatmap, timeline);
    const maxValue = Math.max(1, ...matrix.flatMap((row) => row.values));

    const head = document.createElement("div");
    head.className = "heatmap-head";
    head.innerHTML = `<span></span>${Array.from({ length: 24 }, (_, hour) => `<span>${hour}</span>`).join("")}`;
    root.appendChild(head);

    matrix.forEach((row) => {
        const line = document.createElement("div");
        line.className = "heatmap-row";
        const cells = row.values
            .map((value) => {
                const strength = value / maxValue;
                const background = `rgba(20, 140, 255, ${0.08 + strength * 0.82})`;
                const color = strength > 0.55 ? "#f5fbff" : chartPalette.ink;
                return `<div class="heatmap-cell" style="background:${background};color:${color}">${value || ""}</div>`;
            })
            .join("");
        line.innerHTML = `<div class="heatmap-label">${row.day}</div>${cells}`;
        root.appendChild(line);
    });
}

function syncVisualizationPage() {
    if (state.visualizationFrame) {
        window.cancelAnimationFrame(state.visualizationFrame);
        state.visualizationFrame = null;
    }

    if (state.currentPage !== "visualizations") {
        destroyCharts();
        return;
    }

    state.visualizationFrame = window.requestAnimationFrame(() => {
        state.visualizationFrame = null;
        renderVisualizationPage(state.latestVisuals);
    });
}

function renderVisualizationPage(visuals = {}) {
    const timeline = asArray(visuals.timeline);
    renderTimelineChart(timeline);
    renderHistogramChart(asArray(visuals.histogram), timeline);
    renderLocationChart(asArray(visuals.locationBreakdown));
    renderCategoryChart(asArray(visuals.categoryBreakdown));
    renderHeatmap(visuals.heatmap, timeline);
}

function upsertChart(canvasId, config) {
    const canvas = document.getElementById(canvasId);
    if (!canvas || typeof Chart === "undefined") {
        return;
    }

    if (state.chartInstances[canvasId]) {
        state.chartInstances[canvasId].destroy();
    }

    state.chartInstances[canvasId] = new Chart(canvas, config);
}

function destroyCharts() {
    Object.values(state.chartInstances).forEach((chart) => chart?.destroy());
    state.chartInstances = {};
}

function baseChartOptions(extra = {}) {
    return {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
            legend: {
                labels: { color: chartPalette.ink, usePointStyle: true, boxWidth: 10 }
            }
        },
        ...extra
    };
}

function tooltipBackground() {
    return state.theme === "dark" ? "rgba(4, 12, 22, 0.96)" : "rgba(27, 49, 67, 0.94)";
}

async function api(url, init = {}) {
    const response = await fetch(url, {
        credentials: "same-origin",
        headers: {
            "Content-Type": "application/json",
            ...(init.headers || {})
        },
        ...init
    });

    if (!response.ok) {
        let message = "Request failed.";
        try {
            const errorPayload = await response.json();
            message = anyOf(errorPayload, ["message", "error"], message);
        } catch (error) {
            message = response.status === 401 ? "Invalid credentials." : `Request failed with status ${response.status}.`;
        }
        throw new Error(message);
    }

    if (response.status === 204) {
        return null;
    }

    const contentType = response.headers.get("content-type") || "";
    return contentType.includes("application/json") ? response.json() : response.text();
}

function fillSelect(id, items, selected) {
    const select = document.getElementById(id);
    if (!select) {
        return;
    }

    const normalized = items.map((item) => {
        if (item && typeof item === "object") {
            const label = anyOf(item, ["label", "name", "value"], "");
            return { value: String(anyOf(item, ["value", "label", "name"], label)), label: String(label) };
        }
        return { value: String(item), label: String(item) };
    });

    select.innerHTML = normalized
        .map(
            (item) =>
                `<option value="${escapeAttribute(item.value)}" ${String(item.value) === String(selected) ? "selected" : ""}>${escapeHtml(item.label)}</option>`
        )
        .join("");
}

function prependAll(items) {
    const values = items.map((item) => (typeof item === "object" ? item.value : item));
    return values.includes("All") ? items : ["All", ...items];
}

function anyOf(source, keys, fallback = null) {
    if (source == null) {
        return fallback;
    }

    for (const key of keys) {
        if (Object.prototype.hasOwnProperty.call(source, key) && source[key] != null) {
            return source[key];
        }
    }

    return fallback;
}

function asArray(value) {
    if (Array.isArray(value)) {
        return value;
    }
    return value == null ? [] : [value];
}

function normalizeDistribution(entries) {
    return entries.map((entry) => ({
        label: String(anyOf(entry, ["label", "bucket", "range"], "")),
        value: Number(anyOf(entry, ["value", "count", "transactions"], 0))
    }));
}

function buildHistogramFromTimeline(timeline) {
    const amounts = timeline
        .map((entry) => Number(anyOf(entry, ["amount", "value"], 0)))
        .filter((value) => !Number.isNaN(value));

    if (!amounts.length) {
        return [];
    }

    const max = Math.max(...amounts);
    const min = Math.min(...amounts);
    const span = Math.max(1, max - min);
    const bucketSize = span / 6;
    const buckets = Array.from({ length: 6 }, (_, index) => ({
        label: `${formatCurrency(min + bucketSize * index)} - ${formatCurrency(min + bucketSize * (index + 1))}`,
        value: 0
    }));

    amounts.forEach((amount) => {
        const bucketIndex = Math.min(buckets.length - 1, Math.floor((amount - min) / bucketSize));
        buckets[bucketIndex].value += 1;
    });

    return buckets;
}

function normalizeBreakdown(entries) {
    return entries.map((entry) => ({
        label: String(anyOf(entry, ["label", "name", "category", "location"], "Unknown")),
        value: Number(anyOf(entry, ["value", "count", "transactions"], 0))
    }));
}

function normalizeHeatmap(heatmap, timeline) {
    if (Array.isArray(heatmap) && heatmap.length && typeof heatmap[0] === "object" && !Array.isArray(heatmap[0])) {
        const grouped = new Map(daysOfWeek.map((day) => [day, Array(24).fill(0)]));
        heatmap.forEach((entry) => {
            const day = anyOf(entry, ["day", "weekday", "label"], "");
            const hour = Number(anyOf(entry, ["hour", "x"], 0));
            const count = Number(anyOf(entry, ["count", "value", "y"], 0));
            if (grouped.has(day) && hour >= 0 && hour < 24) {
                grouped.get(day)[hour] = count;
            }
        });
        return daysOfWeek.map((day) => ({ day, values: grouped.get(day) }));
    }

    if (Array.isArray(heatmap) && heatmap.length && Array.isArray(heatmap[0])) {
        return daysOfWeek.map((day, index) => ({
            day,
            values: Array.isArray(heatmap[index]) ? heatmap[index] : Array(24).fill(0)
        }));
    }

    const grouped = new Map(daysOfWeek.map((day) => [day, Array(24).fill(0)]));
    timeline.forEach((entry) => {
        const raw = anyOf(entry, ["timestamp", "time", "label"], "");
        const date = parseApiDate(raw);
        if (!date || Number.isNaN(date.getTime())) {
            return;
        }
        const day = daysOfWeek[(date.getDay() + 6) % 7];
        const hour = date.getHours();
        grouped.get(day)[hour] += 1;
    });
    return daysOfWeek.map((day) => ({ day, values: grouped.get(day) }));
}

function normalizeDate(value) {
    if (!value) {
        return "";
    }
    if (typeof value === "string") {
        return value.slice(0, 10);
    }
    return "";
}

function normalizeRiskLevel(value) {
    const normalized = String(value || "").toLowerCase();
    if (normalized.includes("high") || normalized.includes("error") || normalized.includes("danger")) {
        return "high";
    }
    if (normalized.includes("warn") || normalized.includes("medium") || normalized.includes("moderate")) {
        return "medium";
    }
    if (normalized.includes("success") || normalized.includes("low") || normalized.includes("safe")) {
        return "low";
    }
    return "neutral";
}

function clamp01(value) {
    const numeric = Number(value);
    if (Number.isNaN(numeric)) {
        return 0;
    }
    if (numeric > 1) {
        return Math.max(0, Math.min(1, numeric / 100));
    }
    return Math.max(0, Math.min(1, numeric));
}

function formatCurrency(value) {
    const numeric = Number(value) || 0;
    return new Intl.NumberFormat("en-IN", {
        style: "currency",
        currency: "INR",
        maximumFractionDigits: 2
    }).format(numeric);
}

function formatInteger(value) {
    return new Intl.NumberFormat("en-IN").format(Number(value) || 0);
}

function formatPercent(value) {
    const numeric = Number(value) || 0;
    return `${numeric.toFixed(1)}%`;
}

function parseApiDate(value) {
    if (!value || typeof value !== "string") {
        return null;
    }

    const normalized = value
        .trim()
        .replace(" ", "T")
        .replace(/(\.\d{3})\d+/, "$1");
    const parsed = new Date(normalized);
    return Number.isNaN(parsed.getTime()) ? null : parsed;
}

function formatTimelineLabel(date) {
    return new Intl.DateTimeFormat("en-IN", {
        day: "2-digit",
        month: "short",
        hour: "2-digit",
        minute: "2-digit"
    }).format(date);
}

function formatDateTime(value) {
    if (!value) {
        return "-";
    }
    const date = typeof value === "string" ? parseApiDate(value) : null;
    if (!date) {
        return String(value);
    }
    return new Intl.DateTimeFormat("en-IN", {
        dateStyle: "medium",
        timeStyle: "short"
    }).format(date);
}

function setText(id, value) {
    const node = document.getElementById(id);
    if (node) {
        node.textContent = value;
    }
}

function buildAreaGradient(canvasId, topColor, bottomColor) {
    const canvas = document.getElementById(canvasId);
    const context = canvas?.getContext("2d");
    if (!context) {
        return topColor;
    }

    const gradient = context.createLinearGradient(0, 0, 0, canvas.clientHeight || 340);
    gradient.addColorStop(0, topColor);
    gradient.addColorStop(1, bottomColor);
    return gradient;
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#39;");
}

function escapeAttribute(value) {
    return escapeHtml(value).replaceAll("`", "&#96;");
}

function showFraudAlertPopup(payload, context = {}) {
    const modal = document.getElementById("fraudAlertModal");
    const closeButton = document.getElementById("fraudAlertCloseButton");
    const probability = clamp01(
        context.probability ?? anyOf(payload, ["fraudProbability", "probability", "scoreProbability"], 0)
    );
    const riskScore = Number(context.riskScore ?? anyOf(payload, ["riskScore", "score"], probability * 100));
    const factors = Array.isArray(context.factors)
        ? context.factors
        : asArray(anyOf(payload, ["factors", "reasons", "signals"]));
    const verdictLabel = anyOf(payload, ["label", "prediction", "verdict"], "Fraud detected");
    const notificationAttempted = anyOf(payload, ["notificationAttempted"], false);
    const notificationMessage = notificationAttempted
        ? anyOf(
            payload,
            ["notificationMessage"],
            anyOf(payload, ["notificationSent"], false)
                ? "Fraud alert email sent."
                : "Fraud detected, but the alert email could not be sent."
        )
        : "Fraud was predicted for this transaction profile.";

    if (!modal || !closeButton) {
        return;
    }

    if (modal.hidden) {
        state.lastFocusedElement = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    }

    setText("fraudAlertTitle", verdictLabel);
    setText(
        "fraudAlertMessage",
        `${formatPercent(probability * 100)} fraud probability was predicted for the submitted transaction profile. Review the model signals and email delivery status below.`
    );
    setText("fraudAlertProbability", formatPercent(probability * 100));
    setText("fraudAlertRiskScore", `${Math.round(riskScore)} / 100`);
    setText("fraudAlertNotification", notificationMessage);

    const factorList = document.getElementById("fraudAlertFactors");
    factorList.innerHTML = "";
    (factors.length ? factors : ["No additional model factors were returned."]).forEach((factor) => {
        const item = document.createElement("li");
        item.textContent = typeof factor === "string" ? factor : anyOf(factor, ["message", "label"], "Model factor");
        factorList.appendChild(item);
    });

    modal.hidden = false;
    modal.setAttribute("aria-hidden", "false");
    document.body.classList.add("modal-open");
    window.requestAnimationFrame(() => closeButton.focus());
}

function closeFraudAlertPopup() {
    const modal = document.getElementById("fraudAlertModal");
    if (!modal || modal.hidden) {
        return;
    }

    modal.hidden = true;
    modal.setAttribute("aria-hidden", "true");
    document.body.classList.remove("modal-open");

    if (state.lastFocusedElement && document.contains(state.lastFocusedElement)) {
        state.lastFocusedElement.focus();
    }
    state.lastFocusedElement = null;
}

function handleFraudAlertBackdropClick(event) {
    if (event.target === event.currentTarget) {
        closeFraudAlertPopup();
    }
}

function handleFraudAlertKeydown(event) {
    if (event.key === "Escape") {
        closeFraudAlertPopup();
    }
}

let toastTimer;
function showToast(message) {
    const toast = document.getElementById("toast");
    toast.textContent = message;
    toast.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
        toast.hidden = true;
    }, 3200);
}

function capitalize(value) {
    const text = String(value || "");
    return text ? text.charAt(0).toUpperCase() + text.slice(1) : "";
}
