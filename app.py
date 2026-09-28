"""
ROYAL EVENTS — Supply Manifest & Material Planner (Python / Flask version)

Companion implementation of the ROYAL EVENTS material planner, built as a
server-rendered Flask app. Demonstrates the same data model as the original
HTML/CSS/JS single-page app: event types, a materials catalog grouped by
category, a lightweight login, and booking/contact details for the customer
and the assigned event manager.

Run:
    pip install -r requirements.txt
    python app.py
Then open http://127.0.0.1:5000
"""

from flask import Flask, request, session, redirect, url_for, render_template_string
import json
import os

app = Flask(__name__)
app.secret_key = "royal-events-demo-secret-change-me"

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
USERS_FILE = os.path.join(BASE_DIR, "users.json")
BOOKINGS_FILE = os.path.join(BASE_DIR, "bookings.json")

EVENT_TYPES = [
    "Wedding", "Birthday Party", "Corporate Conference", "Trade Show / Exhibition",
    "Concert / Festival", "Sports Event", "Religious Ceremony",
    "Graduation Ceremony", "Private House Party",
]

MATERIALS = [
    {"name": "Folding chairs", "category": "Furniture & Seating", "qty": 100, "events": "all"},
    {"name": "Round banquet tables", "category": "Furniture & Seating", "qty": 12, "events": "all"},
    {"name": "Registration desk", "category": "Furniture & Seating", "qty": 2,
     "events": ["Corporate Conference", "Trade Show / Exhibition", "Graduation Ceremony"]},

    {"name": "Table linens & runners", "category": "Decor & Ambience", "qty": 12,
     "events": ["Wedding", "Birthday Party", "Religious Ceremony", "Corporate Conference"]},
    {"name": "String lights / fairy lights", "category": "Decor & Ambience", "qty": 6,
     "events": ["Wedding", "Birthday Party", "Private House Party", "Concert / Festival"]},
    {"name": "Backdrop / photo wall", "category": "Decor & Ambience", "qty": 1, "events": "all"},

    {"name": "PA system & speakers", "category": "Audio/Visual & Tech", "qty": 1, "events": "all"},
    {"name": "Wireless microphones", "category": "Audio/Visual & Tech", "qty": 4,
     "events": ["Wedding", "Corporate Conference", "Graduation Ceremony", "Religious Ceremony", "Sports Event"]},
    {"name": "Projector & screen", "category": "Audio/Visual & Tech", "qty": 1,
     "events": ["Corporate Conference", "Trade Show / Exhibition", "Graduation Ceremony"]},

    {"name": "Dinnerware & cutlery sets", "category": "Catering & Serveware", "qty": 150,
     "events": ["Wedding", "Birthday Party", "Religious Ceremony", "Corporate Conference"]},
    {"name": "Coffee & beverage station", "category": "Catering & Serveware", "qty": 1, "events": "all"},
    {"name": "Ice bins & coolers", "category": "Catering & Serveware", "qty": 6,
     "events": ["Sports Event", "Concert / Festival", "Private House Party"]},

    {"name": "Welcome / entrance banner", "category": "Signage & Branding", "qty": 1, "events": "all"},
    {"name": "Directional & wayfinding signs", "category": "Signage & Branding", "qty": 10, "events": "all"},
    {"name": "Sponsor banners", "category": "Signage & Branding", "qty": 6,
     "events": ["Sports Event", "Trade Show / Exhibition", "Concert / Festival"]},

    {"name": "Tents / marquees", "category": "Power & Logistics", "qty": 2,
     "events": ["Wedding", "Concert / Festival", "Sports Event", "Trade Show / Exhibition"]},
    {"name": "Generator / backup power", "category": "Power & Logistics", "qty": 1,
     "events": ["Concert / Festival", "Sports Event", "Trade Show / Exhibition"]},
    {"name": "Climate control (heaters/fans)", "category": "Power & Logistics", "qty": 2, "events": "all"},

    {"name": "First-aid kit", "category": "Safety & Sanitation", "qty": 2, "events": "all"},
    {"name": "Fire extinguishers", "category": "Safety & Sanitation", "qty": 2, "events": "all"},
    {"name": "Portable restrooms", "category": "Safety & Sanitation", "qty": 4,
     "events": ["Concert / Festival", "Sports Event", "Wedding"]},

    {"name": "Trash bins & liners", "category": "Cleanup & Waste", "qty": 10, "events": "all"},
    {"name": "Recycling bins", "category": "Cleanup & Waste", "qty": 6, "events": "all"},
    {"name": "Cleaning supplies kit", "category": "Cleanup & Waste", "qty": 1, "events": "all"},
]

CATEGORY_ORDER = [
    "Furniture & Seating", "Decor & Ambience", "Audio/Visual & Tech", "Catering & Serveware",
    "Signage & Branding", "Power & Logistics", "Safety & Sanitation", "Cleanup & Waste",
]


def load_json(path, default):
    if os.path.exists(path):
        with open(path, "r") as f:
            return json.load(f)
    return default


def save_json(path, data):
    with open(path, "w") as f:
        json.dump(data, f, indent=2)


def materials_for_event(event_type):
    items = [m for m in MATERIALS if m["events"] == "all" or event_type in m["events"]]
    grouped = {}
    for cat in CATEGORY_ORDER:
        cat_items = [m for m in items if m["category"] == cat]
        if cat_items:
            grouped[cat] = cat_items
    return grouped


PAGE_TEMPLATE = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>ROYAL EVENTS — Supply Manifest (Python/Flask)</title>
<style>
  body{ font-family: Arial, sans-serif; background:#EDEAE0; color:#23262B; margin:0; padding:24px; }
  .wrap{ max-width: 900px; margin:0 auto; }
  header{ border:2px solid #23262B; background:#F6F4EC; padding:18px 22px; margin-bottom:20px; }
  header h1{ margin:0 0 2px; }
  header p{ margin:0; color:#5B5D57; font-size:13px; }
  .card{ border:1px solid #23262B; background:#F6F4EC; padding:16px 20px; margin-bottom:20px; }
  label{ display:block; font-size:12.5px; color:#5B5D57; margin:10px 0 4px; }
  input, select, textarea{ width:100%; padding:8px; border:1.5px solid #23262B; font-family: Arial; box-sizing:border-box;}
  button{ background:#2F6F6B; color:#fff; border:none; padding:10px 16px; margin-top:12px; cursor:pointer; }
  .category h3{ margin-bottom:6px; }
  .item{ display:flex; justify-content:space-between; padding:6px 0; border-bottom:1px solid #ddd; }
  .userbar{ text-align:right; font-size:13px; margin-bottom:10px; }
  .role{ border:1px solid #C79A3D; color:#C79A3D; padding:1px 7px; font-size:11px; }
  a{ color:#1F4E4B; }
  .flash{ color:#A3402F; font-size:13px; }
</style>
</head>
<body>
<div class="wrap">
  <header>
    <h1>ROYAL EVENTS</h1>
    <p>Supply Manifest &amp; Material Planner — Python / Flask edition</p>
  </header>

  {% if not session.get('user') %}
    <div class="card">
      <h2>Sign in</h2>
      {% if error %}<p class="flash">{{ error }}</p>{% endif %}
      <form method="POST" action="{{ url_for('login') }}">
        <label>Role</label>
        <select name="role">
          <option>Customer</option>
          <option>Event Manager</option>
        </select>
        <label>Full name</label>
        <input type="text" name="name" required>
        <label>Email</label>
        <input type="email" name="email" required>
        <label>Password</label>
        <input type="password" name="password" required>
        <button type="submit">Sign in</button>
      </form>
      <p style="font-size:11.5px;color:#5B5D57;margin-top:10px;">
        First time with this email creates the account. Demo-only auth: passwords are
        stored in plain text in users.json for simplicity — do not reuse a real password.
      </p>
    </div>
  {% else %}
    <div class="userbar">
      Signed in as <b>{{ session['user']['name'] }}</b>
      <span class="role">{{ session['user']['role'] }}</span>
      &nbsp;|&nbsp; <a href="{{ url_for('logout') }}">Sign out</a>
    </div>

    <div class="card">
      <form method="GET" action="{{ url_for('index') }}">
        <label>Event type</label>
        <select name="event" onchange="this.form.submit()">
          {% for e in event_types %}
            <option value="{{ e }}" {% if e == current_event %}selected{% endif %}>{{ e }}</option>
          {% endfor %}
        </select>
      </form>
    </div>

    <div class="card">
      <h2>Booking &amp; Contacts</h2>
      <form method="POST" action="{{ url_for('save_booking') }}">
        <input type="hidden" name="event" value="{{ current_event }}">
        <h3>Customer booking this event</h3>
        <label>Full name</label><input type="text" name="cust_name" value="{{ booking.get('cust_name','') }}">
        <label>Phone</label><input type="tel" name="cust_phone" value="{{ booking.get('cust_phone','') }}">
        <label>Email</label><input type="email" name="cust_email" value="{{ booking.get('cust_email','') }}">
        <label>Event date</label><input type="date" name="cust_date" value="{{ booking.get('cust_date','') }}">
        <label>Venue / address</label><input type="text" name="cust_venue" value="{{ booking.get('cust_venue','') }}">
        <label>Notes</label><textarea name="cust_notes">{{ booking.get('cust_notes','') }}</textarea>

        <h3>Event manager assigned</h3>
        <label>Full name</label><input type="text" name="mgr_name" value="{{ booking.get('mgr_name','') }}">
        <label>Phone</label><input type="tel" name="mgr_phone" value="{{ booking.get('mgr_phone','') }}">
        <label>Email</label><input type="email" name="mgr_email" value="{{ booking.get('mgr_email','') }}">
        <label>Company / role</label><input type="text" name="mgr_role" value="{{ booking.get('mgr_role','') }}">

        <button type="submit">Save booking details</button>
      </form>
    </div>

    <div class="card">
      <h2>Materials for {{ current_event }}</h2>
      {% for cat, items in grouped.items() %}
        <div class="category">
          <h3>{{ cat }}</h3>
          {% for m in items %}
            <div class="item"><span>{{ m.name }}</span><span>qty: {{ m.qty }}</span></div>
          {% endfor %}
        </div>
      {% endfor %}
    </div>
  {% endif %}
</div>
</body>
</html>
"""


@app.route("/", methods=["GET"])
def index():
    users = load_json(USERS_FILE, {})
    bookings = load_json(BOOKINGS_FILE, {})
    current_event = request.args.get("event", EVENT_TYPES[0])
    if current_event not in EVENT_TYPES:
        current_event = EVENT_TYPES[0]
    grouped = materials_for_event(current_event)
    booking = bookings.get(current_event, {})
    return render_template_string(
        PAGE_TEMPLATE,
        event_types=EVENT_TYPES,
        current_event=current_event,
        grouped=grouped,
        booking=booking,
        error=None,
    )


@app.route("/login", methods=["POST"])
def login():
    users = load_json(USERS_FILE, {})
    name = request.form["name"].strip()
    email = request.form["email"].strip().lower()
    password = request.form["password"]
    role = request.form["role"]

    if email in users:
        if users[email]["password"] != password:
            return render_template_string(
                PAGE_TEMPLATE, event_types=EVENT_TYPES, current_event=EVENT_TYPES[0],
                grouped={}, booking={}, error="That password doesn't match this email."
            )
        users[email]["name"] = name
        users[email]["role"] = role
    else:
        users[email] = {"name": name, "email": email, "password": password, "role": role}

    save_json(USERS_FILE, users)
    session["user"] = {"name": name, "email": email, "role": role}
    return redirect(url_for("index"))


@app.route("/logout")
def logout():
    session.pop("user", None)
    return redirect(url_for("index"))


@app.route("/booking", methods=["POST"])
def save_booking():
    if "user" not in session:
        return redirect(url_for("index"))
    bookings = load_json(BOOKINGS_FILE, {})
    event = request.form["event"]
    bookings[event] = {
        "cust_name": request.form.get("cust_name", ""),
        "cust_phone": request.form.get("cust_phone", ""),
        "cust_email": request.form.get("cust_email", ""),
        "cust_date": request.form.get("cust_date", ""),
        "cust_venue": request.form.get("cust_venue", ""),
        "cust_notes": request.form.get("cust_notes", ""),
        "mgr_name": request.form.get("mgr_name", ""),
        "mgr_phone": request.form.get("mgr_phone", ""),
        "mgr_email": request.form.get("mgr_email", ""),
        "mgr_role": request.form.get("mgr_role", ""),
    }
    save_json(BOOKINGS_FILE, bookings)
    return redirect(url_for("index", event=event))


if __name__ == "__main__":
    app.run(debug=True)
