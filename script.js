
// Display messages to the user
function showMessage(message) {
    document.getElementById("message").textContent = message;
}

// Send form data to the Java backend
async function sendRequest(url, form) {

    const formData = new FormData(form);
    const params = new URLSearchParams();

    for (const [key, value] of formData.entries()) {
        params.append(key, value);
    }

    try {

        const response = await fetch(url, {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded"
            },
            body: params.toString()
        });

        const result = await response.json();

        showMessage(result.message);

        await loadDashboard();
        await loadSlots();
        await loadVehicles();

    } catch (error) {

        showMessage("Unable to connect to Java server.");
        console.error(error);
    }
}

// Register vehicle
document.getElementById("vehicleForm")
    .addEventListener("submit", function(event) {

        event.preventDefault();

        sendRequest("/api/vehicles", this);
        this.reset();
    });

// Add parking slot
document.getElementById("slotForm")
    .addEventListener("submit", function(event) {

        event.preventDefault();

        sendRequest("/api/slots", this);
        this.reset();
    });

// Vehicle entry
document.getElementById("entryForm")
    .addEventListener("submit", function(event) {

        event.preventDefault();

        sendRequest("/api/entry", this);
        this.reset();
    });

// Vehicle exit
document.getElementById("exitForm")
    .addEventListener("submit", function(event) {

        event.preventDefault();

        sendRequest("/api/exit", this);
        this.reset();
    });

// Load dashboard information
async function loadDashboard() {

    try {

        const response = await fetch("/api/dashboard");
        const data = await response.json();

        document.getElementById("available2W").textContent =
            data.available2W;

        document.getElementById("occupied2W").textContent =
            data.occupied2W;

        document.getElementById("available4W").textContent =
            data.available4W;

        document.getElementById("occupied4W").textContent =
            data.occupied4W;

        document.getElementById("totalVehicles").textContent =
            data.totalVehicles;

        document.getElementById("parkedVehicles").textContent =
            data.parkedVehicles;

    } catch (error) {
        console.error("Dashboard loading error:", error);
    }
}

// Display all parking slots
async function loadSlots() {

    try {

        const response = await fetch("/api/slots");
        const slots = await response.json();

        const slotList = document.getElementById("slotList");

        slotList.innerHTML = "";

        if (slots.length === 0) {
            slotList.textContent = "No parking slots added yet.";
            return;
        }

        slots.forEach(slot => {

            const div = document.createElement("div");

            div.className = slot.occupied
                ? "slot occupied"
                : "slot available";

            const title = document.createElement("h3");
            title.textContent = slot.number;

            const category = document.createElement("p");
            category.textContent =
                slot.category === "2W"
                    ? "Two Wheeler"
                    : "Four Wheeler";

            const status = document.createElement("p");
            status.textContent = slot.occupied
                ? "Occupied"
                : "Available";

            const vehicle = document.createElement("p");
            vehicle.textContent = slot.occupied
                ? "Vehicle: " + slot.vehicleNumber
                : "No vehicle";

            div.append(title, category, status, vehicle);
            slotList.appendChild(div);
        });

    } catch (error) {
        console.error("Slot loading error:", error);
    }
}

// Display registered vehicles
async function loadVehicles() {

    try {

        const response = await fetch("/api/vehicles");
        const vehicles = await response.json();

        const vehicleList = document.getElementById("vehicleList");

        vehicleList.innerHTML = "";

        if (vehicles.length === 0) {
            vehicleList.textContent = "No vehicles registered yet.";
            return;
        }

        vehicles.forEach(vehicle => {

            const p = document.createElement("p");

            p.textContent =
                vehicle.number + " - " +
                (vehicle.category === "2W"
                    ? "Two Wheeler"
                    : "Four Wheeler");

            vehicleList.appendChild(p);
        });

    } catch (error) {
        console.error("Vehicle loading error:", error);
    }
}

// Check category conflicts
async function checkConflicts() {

    try {

        const response = await fetch("/api/conflicts");
        const conflicts = await response.json();

        const conflictList =
            document.getElementById("conflictList");

        conflictList.innerHTML = "";

        if (conflicts.length === 0) {

            conflictList.textContent =
                "No parking conflicts found!";

            return;
        }

        conflicts.forEach(conflict => {

            const p = document.createElement("p");

            p.textContent =
                "Vehicle " + conflict.vehicle +
                " (" + conflict.vehicleCategory + ")" +
                " is assigned to slot " + conflict.slot +
                " (" + conflict.slotCategory + ")";

            conflictList.appendChild(p);
        });

    } catch (error) {
        console.error("Conflict checking error:", error);
    }
}

// Load initial information when the page opens
window.addEventListener("DOMContentLoaded", function() {

    loadDashboard();
    loadSlots();
    loadVehicles();

});
