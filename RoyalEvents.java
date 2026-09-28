/**
 * ROYAL EVENTS — Supply Manifest & Material Planner (Java version)
 *
 * Companion implementation of the ROYAL EVENTS material planner, using only
 * the JDK's built-in com.sun.net.httpserver.HttpServer — no external
 * dependencies, no build tool required. Same data model as the original:
 * event types, a materials catalog grouped by category, a lightweight
 * login, and booking/contact details for the customer and the assigned
 * event manager.
 *
 * NOTE: unlike the Python, PHP and Node.js versions, this demo keeps users
 * and bookings in memory only (no file persistence) to keep a single-file,
 * dependency-free build. Data resets when the server restarts.
 *
 * Run:
 *     javac RoyalEvents.java
 *     java RoyalEvents
 * Then open http://localhost:8080
 */

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class RoyalEvents {

    static final String[] EVENT_TYPES = {
        "Wedding", "Birthday Party", "Corporate Conference", "Trade Show / Exhibition",
        "Concert / Festival", "Sports Event", "Religious Ceremony",
        "Graduation Ceremony", "Private House Party"
    };

    static final String[] CATEGORY_ORDER = {
        "Furniture & Seating", "Decor & Ambience", "Audio/Visual & Tech", "Catering & Serveware",
        "Signage & Branding", "Power & Logistics", "Safety & Sanitation", "Cleanup & Waste"
    };

    static class Material {
        String name, category;
        int qty;
        String[] events; // null means "all"
        Material(String n, String c, int q, String... e){ name=n; category=c; qty=q; events=(e.length==0?null:e); }
        boolean appliesTo(String evt){ return events == null || Arrays.asList(events).contains(evt); }
    }

    static final List<Material> MATERIALS = Arrays.asList(
        new Material("Folding chairs", "Furniture & Seating", 100),
        new Material("Round banquet tables", "Furniture & Seating", 12),
        new Material("Registration desk", "Furniture & Seating", 2, "Corporate Conference", "Trade Show / Exhibition", "Graduation Ceremony"),

        new Material("Table linens & runners", "Decor & Ambience", 12, "Wedding", "Birthday Party", "Religious Ceremony", "Corporate Conference"),
        new Material("String lights / fairy lights", "Decor & Ambience", 6, "Wedding", "Birthday Party", "Private House Party", "Concert / Festival"),
        new Material("Backdrop / photo wall", "Decor & Ambience", 1),

        new Material("PA system & speakers", "Audio/Visual & Tech", 1),
        new Material("Wireless microphones", "Audio/Visual & Tech", 4, "Wedding", "Corporate Conference", "Graduation Ceremony", "Religious Ceremony", "Sports Event"),
        new Material("Projector & screen", "Audio/Visual & Tech", 1, "Corporate Conference", "Trade Show / Exhibition", "Graduation Ceremony"),

        new Material("Dinnerware & cutlery sets", "Catering & Serveware", 150, "Wedding", "Birthday Party", "Religious Ceremony", "Corporate Conference"),
        new Material("Coffee & beverage station", "Catering & Serveware", 1),
        new Material("Ice bins & coolers", "Catering & Serveware", 6, "Sports Event", "Concert / Festival", "Private House Party"),

        new Material("Welcome / entrance banner", "Signage & Branding", 1),
        new Material("Directional & wayfinding signs", "Signage & Branding", 10),
        new Material("Sponsor banners", "Signage & Branding", 6, "Sports Event", "Trade Show / Exhibition", "Concert / Festival"),

        new Material("Tents / marquees", "Power & Logistics", 2, "Wedding", "Concert / Festival", "Sports Event", "Trade Show / Exhibition"),
        new Material("Generator / backup power", "Power & Logistics", 1, "Concert / Festival", "Sports Event", "Trade Show / Exhibition"),
        new Material("Climate control (heaters/fans)", "Power & Logistics", 2),

        new Material("First-aid kit", "Safety & Sanitation", 2),
        new Material("Fire extinguishers", "Safety & Sanitation", 2),
        new Material("Portable restrooms", "Safety & Sanitation", 4, "Concert / Festival", "Sports Event", "Wedding"),

        new Material("Trash bins & liners", "Cleanup & Waste", 10),
        new Material("Recycling bins", "Cleanup & Waste", 6),
        new Material("Cleaning supplies kit", "Cleanup & Waste", 1)
    );

    static class User { String name, email, password, role; }
    static class Booking {
        String custName="", custPhone="", custEmail="", custDate="", custVenue="", custNotes="";
        String mgrName="", mgrPhone="", mgrEmail="", mgrRole="";
    }

    static final Map<String, User> USERS = new HashMap<>();          // email -> User
    static final Map<String, Booking> BOOKINGS = new HashMap<>();    // eventType -> Booking
    static final Map<String, String> SESSIONS = new HashMap<>();     // token -> email

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", RoyalEvents::handleIndex);
        server.createContext("/login", RoyalEvents::handleLogin);
        server.createContext("/logout", RoyalEvents::handleLogout);
        server.createContext("/booking", RoyalEvents::handleBooking);
        server.setExecutor(null);
        server.start();
        System.out.println("ROYAL EVENTS (Java) running at http://localhost:8080");
    }

    /* ---------- helpers ---------- */

    static Map<String,String> parseForm(String body) {
        Map<String,String> map = new HashMap<>();
        if (body == null || body.isEmpty()) return map;
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            String k = urlDecode(kv[0]);
            String v = kv.length > 1 ? urlDecode(kv[1]) : "";
            map.put(k, v);
        }
        return map;
    }
    static String urlDecode(String s){
        try { return URLDecoder.decode(s, StandardCharsets.UTF_8.name()); } catch (Exception e) { return s; }
    }
    static String readBody(HttpExchange ex) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InputStream in = ex.getRequestBody();
        byte[] buf = new byte[1024]; int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toString(StandardCharsets.UTF_8.name());
    }
    static Map<String,String> parseQuery(String query){
        Map<String,String> map = new HashMap<>();
        if (query == null) return map;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            map.put(urlDecode(kv[0]), kv.length>1 ? urlDecode(kv[1]) : "");
        }
        return map;
    }
    static String getCookie(HttpExchange ex, String name){
        List<String> cookies = ex.getRequestHeaders().get("Cookie");
        if (cookies == null) return null;
        for (String header : cookies) {
            for (String part : header.split(";")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2 && kv[0].equals(name)) return kv[1];
            }
        }
        return null;
    }
    static String esc(String s){
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    static void sendHtml(HttpExchange ex, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
    static void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().set("Location", location);
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }

    static LinkedHashMap<String, List<Material>> materialsForEvent(String evt){
        LinkedHashMap<String, List<Material>> grouped = new LinkedHashMap<>();
        for (String cat : CATEGORY_ORDER) {
            List<Material> items = MATERIALS.stream()
                .filter(m -> m.category.equals(cat) && m.appliesTo(evt))
                .collect(Collectors.toList());
            if (!items.isEmpty()) grouped.put(cat, items);
        }
        return grouped;
    }

    /* ---------- handlers ---------- */

    static void handleIndex(HttpExchange ex) throws IOException {
        Map<String,String> q = parseQuery(ex.getRequestURI().getQuery());
        String event = q.getOrDefault("event", EVENT_TYPES[0]);
        if (!Arrays.asList(EVENT_TYPES).contains(event)) event = EVENT_TYPES[0];

        String token = getCookie(ex, "session");
        User user = (token != null && SESSIONS.containsKey(token)) ? USERS.get(SESSIONS.get(token)) : null;
        Booking booking = BOOKINGS.getOrDefault(event, new Booking());

        sendHtml(ex, renderPage(user, event, booking, null));
    }

    static void handleLogin(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { redirect(ex, "/"); return; }
        Map<String,String> form = parseForm(readBody(ex));
        String name = form.getOrDefault("name","").trim();
        String email = form.getOrDefault("email","").trim().toLowerCase();
        String password = form.getOrDefault("password","");
        String role = form.getOrDefault("role","Customer");

        if (USERS.containsKey(email) && !USERS.get(email).password.equals(password)) {
            sendHtml(ex, renderPage(null, EVENT_TYPES[0], new Booking(), "That password doesn't match this email."));
            return;
        }
        User u = USERS.getOrDefault(email, new User());
        u.name = name; u.email = email; u.password = password; u.role = role;
        USERS.put(email, u);

        String token = UUID.randomUUID().toString();
        SESSIONS.put(token, email);
        ex.getResponseHeaders().add("Set-Cookie", "session=" + token + "; Path=/; HttpOnly");
        redirect(ex, "/");
    }

    static void handleLogout(HttpExchange ex) throws IOException {
        String token = getCookie(ex, "session");
        if (token != null) SESSIONS.remove(token);
        redirect(ex, "/");
    }

    static void handleBooking(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { redirect(ex, "/"); return; }
        String token = getCookie(ex, "session");
        if (token == null || !SESSIONS.containsKey(token)) { redirect(ex, "/"); return; }

        Map<String,String> form = parseForm(readBody(ex));
        String event = form.getOrDefault("event", EVENT_TYPES[0]);
        Booking b = new Booking();
        b.custName = form.getOrDefault("cust_name",""); b.custPhone = form.getOrDefault("cust_phone","");
        b.custEmail = form.getOrDefault("cust_email",""); b.custDate = form.getOrDefault("cust_date","");
        b.custVenue = form.getOrDefault("cust_venue",""); b.custNotes = form.getOrDefault("cust_notes","");
        b.mgrName = form.getOrDefault("mgr_name",""); b.mgrPhone = form.getOrDefault("mgr_phone","");
        b.mgrEmail = form.getOrDefault("mgr_email",""); b.mgrRole = form.getOrDefault("mgr_role","");
        BOOKINGS.put(event, b);

        redirect(ex, "/?event=" + event.replace(" ", "%20"));
    }

    /* ---------- rendering ---------- */

    static String renderPage(User user, String event, Booking booking, String error){
        StringBuilder eventOptions = new StringBuilder();
        for (String e : EVENT_TYPES) {
            eventOptions.append("<option value=\"").append(esc(e)).append("\"")
                .append(e.equals(event) ? " selected" : "").append(">").append(esc(e)).append("</option>");
        }

        String body;
        if (user == null) {
            body = "<div class=\"card\"><h2>Sign in</h2>"
                + (error != null ? "<p class=\"flash\">" + esc(error) + "</p>" : "")
                + "<form method=\"POST\" action=\"/login\">"
                + "<label>Role</label><select name=\"role\"><option>Customer</option><option>Event Manager</option></select>"
                + "<label>Full name</label><input type=\"text\" name=\"name\" required>"
                + "<label>Email</label><input type=\"email\" name=\"email\" required>"
                + "<label>Password</label><input type=\"password\" name=\"password\" required>"
                + "<button type=\"submit\">Sign in</button></form>"
                + "<p style=\"font-size:11.5px;color:#5B5D57;margin-top:10px;\">First time with this email creates "
                + "the account. Demo-only auth held in server memory only — data resets on restart.</p></div>";
        } else {
            StringBuilder catBlocks = new StringBuilder();
            for (Map.Entry<String, List<Material>> entry : materialsForEvent(event).entrySet()) {
                catBlocks.append("<div class=\"category\"><h3>").append(esc(entry.getKey())).append("</h3>");
                for (Material m : entry.getValue()) {
                    catBlocks.append("<div class=\"item\"><span>").append(esc(m.name))
                        .append("</span><span>qty: ").append(m.qty).append("</span></div>");
                }
                catBlocks.append("</div>");
            }

            body = "<div class=\"userbar\">Signed in as <b>" + esc(user.name) + "</b> "
                + "<span class=\"role\">" + esc(user.role) + "</span> &nbsp;|&nbsp; <a href=\"/logout\">Sign out</a></div>"
                + "<div class=\"card\"><form method=\"GET\" action=\"/\"><label>Event type</label>"
                + "<select name=\"event\" onchange=\"this.form.submit()\">" + eventOptions + "</select></form></div>"
                + "<div class=\"card\"><h2>Booking &amp; Contacts</h2>"
                + "<form method=\"POST\" action=\"/booking\">"
                + "<input type=\"hidden\" name=\"event\" value=\"" + esc(event) + "\">"
                + "<h3>Customer booking this event</h3>"
                + "<label>Full name</label><input type=\"text\" name=\"cust_name\" value=\"" + esc(booking.custName) + "\">"
                + "<label>Phone</label><input type=\"tel\" name=\"cust_phone\" value=\"" + esc(booking.custPhone) + "\">"
                + "<label>Email</label><input type=\"email\" name=\"cust_email\" value=\"" + esc(booking.custEmail) + "\">"
                + "<label>Event date</label><input type=\"date\" name=\"cust_date\" value=\"" + esc(booking.custDate) + "\">"
                + "<label>Venue / address</label><input type=\"text\" name=\"cust_venue\" value=\"" + esc(booking.custVenue) + "\">"
                + "<label>Notes</label><textarea name=\"cust_notes\">" + esc(booking.custNotes) + "</textarea>"
                + "<h3>Event manager assigned</h3>"
                + "<label>Full name</label><input type=\"text\" name=\"mgr_name\" value=\"" + esc(booking.mgrName) + "\">"
                + "<label>Phone</label><input type=\"tel\" name=\"mgr_phone\" value=\"" + esc(booking.mgrPhone) + "\">"
                + "<label>Email</label><input type=\"email\" name=\"mgr_email\" value=\"" + esc(booking.mgrEmail) + "\">"
                + "<label>Company / role</label><input type=\"text\" name=\"mgr_role\" value=\"" + esc(booking.mgrRole) + "\">"
                + "<button type=\"submit\">Save booking details</button></form></div>"
                + "<div class=\"card\"><h2>Materials for " + esc(event) + "</h2>" + catBlocks + "</div>";
        }

        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">"
            + "<title>ROYAL EVENTS — Supply Manifest (Java)</title><style>"
            + "body{font-family:Arial,sans-serif;background:#EDEAE0;color:#23262B;margin:0;padding:24px;}"
            + ".wrap{max-width:900px;margin:0 auto;}"
            + "header{border:2px solid #23262B;background:#F6F4EC;padding:18px 22px;margin-bottom:20px;}"
            + "header h1{margin:0 0 2px;} header p{margin:0;color:#5B5D57;font-size:13px;}"
            + ".card{border:1px solid #23262B;background:#F6F4EC;padding:16px 20px;margin-bottom:20px;}"
            + "label{display:block;font-size:12.5px;color:#5B5D57;margin:10px 0 4px;}"
            + "input,select,textarea{width:100%;padding:8px;border:1.5px solid #23262B;font-family:Arial;box-sizing:border-box;}"
            + "button{background:#2F6F6B;color:#fff;border:none;padding:10px 16px;margin-top:12px;cursor:pointer;}"
            + ".category h3{margin-bottom:6px;}"
            + ".item{display:flex;justify-content:space-between;padding:6px 0;border-bottom:1px solid #ddd;}"
            + ".userbar{text-align:right;font-size:13px;margin-bottom:10px;}"
            + ".role{border:1px solid #C79A3D;color:#C79A3D;padding:1px 7px;font-size:11px;}"
            + "a{color:#1F4E4B;} .flash{color:#A3402F;font-size:13px;}"
            + "</style></head><body><div class=\"wrap\">"
            + "<header><h1>ROYAL EVENTS</h1><p>Supply Manifest &amp; Material Planner — Java edition</p></header>"
            + body + "</div></body></html>";
    }
}
