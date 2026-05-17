import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import org.json.simple.*;
import org.json.simple.parser.*;

// ─────────────────────────────────────────────
// Custom Exceptions
// ─────────────────────────────────────────────
class InvalidPatientDataException extends Exception {
    public InvalidPatientDataException(String msg) { super(msg); }
}
class InvalidAppointmentException extends Exception {
    public InvalidAppointmentException(String msg) { super(msg); }
}
class AppointmentConflictException extends Exception {
    public AppointmentConflictException(String msg) { super(msg); }
}

// ─────────────────────────────────────────────
// Domain: Person
// ─────────────────────────────────────────────
class Person {
    private String name, contactNumber;
    private int age;
    public Person(String name, String contactNumber, int age) {
        this.name = name; this.contactNumber = contactNumber; this.age = age;
    }
    public String getName()          { return name; }
    public String getContactNumber() { return contactNumber; }
    public int    getAge()           { return age; }
}

// ─────────────────────────────────────────────
// Domain: Patient
// ─────────────────────────────────────────────
class Patient extends Person {
    private static int counter = 1001;
    private final String patientId;
    private String medicalHistory;

    public Patient(String name, String contact, int age, String medicalHistory) {
        super(name, contact, age);
        this.patientId     = "PAT" + (counter++);
        this.medicalHistory = medicalHistory;
    }
    /** Used when loading from JSON (preserve saved ID) */
    public Patient(String name, String contact, int age, String patientId, String medicalHistory, boolean loaded) {
        super(name, contact, age);
        this.patientId      = patientId;
        this.medicalHistory = medicalHistory;
        // Advance counter past any loaded IDs
        try {
            int num = Integer.parseInt(patientId.replace("PAT",""));
            if (num >= counter) counter = num + 1;
        } catch (NumberFormatException ignored) {}
    }

    public String getPatientId()     { return patientId; }
    public String getMedicalHistory(){ return medicalHistory; }
    public void   setMedicalHistory(String h) { this.medicalHistory = h; }

    @Override public String toString() {
        return String.format("%s (%s) | Age %d", getName(), patientId, getAge());
    }
}

// ─────────────────────────────────────────────
// Domain: Doctor
// ─────────────────────────────────────────────
class Doctor extends Person {
    private final String doctorId, specialization;
    private final Set<LocalDate>              unavailableDates = new HashSet<>();
    private final Map<LocalDate,List<LocalTime>> unavailableTimes = new HashMap<>();

    public Doctor(String name, String contact, int age, String doctorId, String specialization) {
        super(name, contact, age);
        this.doctorId       = doctorId;
        this.specialization = specialization;
    }
    public String getDoctorId()      { return doctorId; }
    public String getSpecialization(){ return specialization; }

    public void addUnavailableDate(LocalDate d) { unavailableDates.add(d); }
    public Set<LocalDate> getUnavailableDates() { return Collections.unmodifiableSet(unavailableDates); }

    public void addUnavailableTime(LocalDate date, LocalTime time) {
        unavailableTimes.computeIfAbsent(date, k -> new ArrayList<>()).add(time);
    }
    public List<LocalTime> getUnavailableTimes(LocalDate date) {
        return unavailableTimes.getOrDefault(date, Collections.emptyList());
    }

    public boolean isAvailableOnDateTime(LocalDate date, LocalTime time) {
        if (unavailableDates.contains(date)) return false;
        List<LocalTime> blocked = unavailableTimes.get(date);
        return blocked == null || !blocked.contains(time);
    }

    @Override public String toString() {
        return String.format("Dr. %s — %s", getName(), specialization);
    }
}

// ─────────────────────────────────────────────
// Domain: Appointment
// ─────────────────────────────────────────────
class Appointment {
    private static int counter = 1000;
    private final String appointmentId;
    private Patient   patient;
    private Doctor    doctor;
    private LocalDate date;
    private LocalTime time;
    private String    status;
    private String    notes;

    /** Normal constructor */
    public Appointment(Patient patient, Doctor doctor, LocalDate date, LocalTime time) {
        this.appointmentId = "APT" + (++counter);
        this.patient = patient; this.doctor = doctor;
        this.date = date;      this.time   = time;
        this.status = "Scheduled"; this.notes = "";
    }
    /** Load from persistence — preserves ID */
    public Appointment(String id, Patient patient, Doctor doctor,
                       LocalDate date, LocalTime time, String status, String notes) {
        this.appointmentId = id;
        this.patient = patient; this.doctor = doctor;
        this.date = date;      this.time   = time;
        this.status = status;   this.notes  = notes != null ? notes : "";
        try {
            int num = Integer.parseInt(id.replace("APT",""));
            if (num >= counter) counter = num;
        } catch (NumberFormatException ignored) {}
    }

    public String    getAppointmentId()  { return appointmentId; }
    public Patient   getPatient()        { return patient; }
    public Doctor    getDoctor()         { return doctor; }
    public LocalDate getDate()           { return date; }
    public LocalTime getTime()           { return time; }
    public String    getStatus()         { return status; }
    public String    getNotes()          { return notes; }

    public void setDoctor(Doctor d)      { this.doctor = d; }
    public void setDate(LocalDate d)     { this.date = d; }
    public void setTime(LocalTime t)     { this.time = t; }
    public void setStatus(String s)      { this.status = s; }
    public void setNotes(String n)       { this.notes = n; }
}

// ─────────────────────────────────────────────
// Persistence (JSON via json-simple)
// ─────────────────────────────────────────────
class DataStore {
    private static final String DATA_FILE = "appointmed_data.json";

    @SuppressWarnings("unchecked")
    public static void save(List<Patient> patients,
                            List<Appointment> appointments,
                            List<Doctor> doctors) {
        JSONObject root = new JSONObject();

        // Patients
        JSONArray pArr = new JSONArray();
        for (Patient p : patients) {
            JSONObject o = new JSONObject();
            o.put("id",      p.getPatientId());
            o.put("name",    p.getName());
            o.put("contact", p.getContactNumber());
            o.put("age",     p.getAge());
            o.put("history", p.getMedicalHistory());
            pArr.add(o);
        }
        root.put("patients", pArr);

        // Appointments
        JSONArray aArr = new JSONArray();
        for (Appointment a : appointments) {
            JSONObject o = new JSONObject();
            o.put("id",        a.getAppointmentId());
            o.put("patientId", a.getPatient().getPatientId());
            o.put("doctorId",  a.getDoctor().getDoctorId());
            o.put("date",      a.getDate().toString());
            o.put("time",      a.getTime().toString());
            o.put("status",    a.getStatus());
            o.put("notes",     a.getNotes());
            aArr.add(o);
        }
        root.put("appointments", aArr);

        // Doctor blocked dates/times
        JSONArray dArr = new JSONArray();
        for (Doctor d : doctors) {
            JSONObject o = new JSONObject();
            o.put("id", d.getDoctorId());
            JSONArray blockedDates = new JSONArray();
            for (LocalDate dt : d.getUnavailableDates()) blockedDates.add(dt.toString());
            o.put("blockedDates", blockedDates);
            // We store blocked times per date as "date|HH:mm" strings
            JSONArray blockedTimes = new JSONArray();
            for (LocalDate dt : d.getUnavailableDates()) {} // dates already done
            // collect from internal map via the public API on all dates next 365 days
            for (int i = 0; i < 365; i++) {
                LocalDate dt = LocalDate.now().plusDays(i);
                for (LocalTime lt : d.getUnavailableTimes(dt))
                    blockedTimes.add(dt.toString() + "|" + lt.toString());
            }
            o.put("blockedTimes", blockedTimes);
            dArr.add(o);
        }
        root.put("doctors", dArr);

        try (FileWriter fw = new FileWriter(DATA_FILE)) {
            fw.write(root.toJSONString());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Save failed: " + e.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void load(List<Patient> patients,
                            List<Appointment> appointments,
                            List<Doctor> doctors) {
        File f = new File(DATA_FILE);
        if (!f.exists()) return;

        try {
            JSONParser parser = new JSONParser();
            JSONObject root   = (JSONObject) parser.parse(new FileReader(f));

            // Load patients
            JSONArray pArr = (JSONArray) root.get("patients");
            if (pArr != null) {
                for (Object obj : pArr) {
                    JSONObject o = (JSONObject) obj;
                    patients.add(new Patient(
                            (String) o.get("name"),
                            (String) o.get("contact"),
                            ((Long) o.get("age")).intValue(),
                            (String) o.get("id"),
                            (String) o.get("history"),
                            true
                    ));
                }
            }

            // Load doctor blocked data
            JSONArray dArr = (JSONArray) root.get("doctors");
            if (dArr != null) {
                for (Object obj : dArr) {
                    JSONObject o  = (JSONObject) obj;
                    String docId  = (String) o.get("id");
                    Doctor doc    = doctors.stream()
                            .filter(d -> d.getDoctorId().equals(docId))
                            .findFirst().orElse(null);
                    if (doc == null) continue;
                    JSONArray bd = (JSONArray) o.get("blockedDates");
                    if (bd != null)
                        for (Object s : bd) doc.addUnavailableDate(LocalDate.parse((String) s));
                    JSONArray bt = (JSONArray) o.get("blockedTimes");
                    if (bt != null)
                        for (Object s : bt) {
                            String[] parts = ((String) s).split("\\|");
                            doc.addUnavailableTime(LocalDate.parse(parts[0]), LocalTime.parse(parts[1]));
                        }
                }
            }

            // Load appointments (patients + doctors must already be loaded)
            JSONArray aArr = (JSONArray) root.get("appointments");
            if (aArr != null) {
                for (Object obj : aArr) {
                    JSONObject o   = (JSONObject) obj;
                    String pid     = (String) o.get("patientId");
                    String did     = (String) o.get("doctorId");
                    Patient  pat   = patients.stream()
                            .filter(p -> p.getPatientId().equals(pid)).findFirst().orElse(null);
                    Doctor   doc   = doctors.stream()
                            .filter(d -> d.getDoctorId().equals(did)).findFirst().orElse(null);
                    if (pat == null || doc == null) continue;
                    appointments.add(new Appointment(
                            (String) o.get("id"), pat, doc,
                            LocalDate.parse((String) o.get("date")),
                            LocalTime.parse((String) o.get("time")),
                            (String) o.get("status"),
                            (String) o.get("notes")
                    ));
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "Load failed (data may be corrupt): " + e.getMessage(),
                    "Load Error", JOptionPane.WARNING_MESSAGE);
        }
    }
}

// ─────────────────────────────────────────────
// Business Logic: Scheduler
// ─────────────────────────────────────────────
class AppointmentScheduler {
    private final List<Patient>     patients     = new ArrayList<>();
    private final List<Doctor>      doctors      = new ArrayList<>();
    private final List<Appointment> appointments = new ArrayList<>();

    private static final LocalTime OPEN  = LocalTime.of(8,  0);
    private static final LocalTime CLOSE = LocalTime.of(17, 0);
    private static final int       SLOT_MINUTES = 30;

    public AppointmentScheduler() {
        seedDoctors();
        DataStore.load(patients, appointments, doctors);
    }

    private void seedDoctors() {
        doctors.add(new Doctor("Miguelito Guerrero",              "101-1010", 37, "DOC01", "Dentist"));
        doctors.add(new Doctor("Michael V.",                      "101-2020", 45, "DOC02", "Dermatologist"));
        doctors.add(new Doctor("Gian Romer Manlapaz",             "101-3030", 30, "DOC03", "Dentist"));
        doctors.add(new Doctor("Dingdong Dantes",                 "101-4040", 45, "DOC04", "General Medicine"));
        doctors.add(new Doctor("Jose Rizal",                      "101-5050", 36, "DOC05", "Ophthalmologist"));
        doctors.add(new Doctor("Hesus Nazareno Tanggol-Montenegro","101-6060", 41, "DOC06", "Dermatologist"));
        doctors.add(new Doctor("Santino Dimaguiba",                "101-7070", 38, "DOC07", "General Medicine"));
        doctors.add(new Doctor("Piolo Pascual",                   "101-8080", 48, "DOC08", "Neurology"));
        doctors.add(new Doctor("Marian Rivera",                   "101-9090", 41, "DOC09", "Dermatologist"));
        doctors.add(new Doctor("Sheena Catacutan",                "202-1010", 42, "DOC10", "OB-Gyne"));
        doctors.add(new Doctor("Coco Martin",                     "202-2020", 41, "DOC11", "General Physician"));
        doctors.add(new Doctor("Ogie Alcasid",                    "202-3030", 45, "DOC12", "Cardiologist"));
        doctors.add(new Doctor("Arthur Nery",                     "202-4040", 28, "DOC13", "Urology"));
    }

    public void save() { DataStore.save(patients, appointments, doctors); }

    // ── Patients ──────────────────────────────
    public void validatePatientData(String name, String contact, String ageStr)
            throws InvalidPatientDataException {
        if (name == null || name.trim().isEmpty())
            throw new InvalidPatientDataException("Name cannot be empty.");
        if (contact == null || !contact.matches("\\d{3}-\\d{4}"))
            throw new InvalidPatientDataException("Contact must be in XXX-XXXX format.");
        try {
            int age = Integer.parseInt(ageStr.trim());
            if (age < 1 || age > 120)
                throw new InvalidPatientDataException("Age must be between 1 and 120.");
        } catch (NumberFormatException e) {
            throw new InvalidPatientDataException("Age must be a valid number.");
        }
    }

    public Patient addPatient(String name, String contact, int age, String history) {
        Patient p = new Patient(name, contact, age, history);
        patients.add(p);
        save();
        return p;
    }

    public List<Patient> getPatients()          { return Collections.unmodifiableList(patients); }
    public List<Doctor>  getDoctors()           { return Collections.unmodifiableList(doctors); }
    public List<Appointment> getAppointments()  { return Collections.unmodifiableList(appointments); }

    // ── Appointments ──────────────────────────
    public void validateAppointment(Doctor doctor, LocalDate date, LocalTime time, Appointment ignore)
            throws InvalidAppointmentException, AppointmentConflictException {

        if (date.isBefore(LocalDate.now()))
            throw new InvalidAppointmentException("Cannot book appointments in the past.");
        if (time.isBefore(OPEN) || time.isAfter(CLOSE))
            throw new InvalidAppointmentException(
                    "Appointments are only available between 08:00 and 17:00.");
        if (time.getMinute() % SLOT_MINUTES != 0)
            throw new InvalidAppointmentException(
                    "Appointments must be on the hour or half-hour (e.g. 9:00, 9:30).");
        if (!doctor.isAvailableOnDateTime(date, time))
            throw new InvalidAppointmentException(
                    "Dr. " + doctor.getName() + " is not available on " + date + " at " + time + ".");

        for (Appointment a : appointments) {
            if (a == ignore || a.getStatus().equals("Cancelled")) continue;
            if (a.getDoctor().getDoctorId().equals(doctor.getDoctorId())
                && a.getDate().equals(date)
                && a.getTime().equals(time)) {
                throw new AppointmentConflictException(
                        "Dr. " + doctor.getName() + " already has a booking at " + time + " on " + date + ".");
            }
        }
    }

    public Appointment bookAppointment(Patient patient, Doctor doctor, LocalDate date, LocalTime time) {
        Appointment a = new Appointment(patient, doctor, date, time);
        appointments.add(a);
        save();
        return a;
    }

    public void cancelAppointment(Appointment a) {
        a.setStatus("Cancelled");
        save();
    }

    public void updateAppointment(Appointment a, Doctor doc, LocalDate date, LocalTime time, String notes)
            throws InvalidAppointmentException, AppointmentConflictException {
        if (a.getStatus().equals("Cancelled"))
            throw new InvalidAppointmentException("Cannot reschedule a cancelled appointment.");
        validateAppointment(doc, date, time, a);
        a.setDoctor(doc); a.setDate(date); a.setTime(time);
        a.setStatus("Rescheduled");
        if (notes != null) a.setNotes(notes);
        save();
    }

    public Appointment findById(String id) {
        return appointments.stream().filter(a -> a.getAppointmentId().equals(id)).findFirst().orElse(null);
    }

    /** Returns available time slots for a given doctor and date */
    public List<LocalTime> getAvailableSlots(Doctor doctor, LocalDate date) {
        List<LocalTime> slots = new ArrayList<>();
        LocalTime t = OPEN;
        while (!t.isAfter(CLOSE)) {
            LocalTime ft = t;
            boolean taken = appointments.stream().anyMatch(a ->
                    !a.getStatus().equals("Cancelled")
                    && a.getDoctor().getDoctorId().equals(doctor.getDoctorId())
                    && a.getDate().equals(date)
                    && a.getTime().equals(ft));
            if (!taken && doctor.isAvailableOnDateTime(date, t)) slots.add(t);
            t = t.plusMinutes(SLOT_MINUTES);
        }
        return slots;
    }

    /** Search appointments by patient name, doctor name, or status */
    public List<Appointment> search(String query) {
        String q = query.toLowerCase().trim();
        if (q.isEmpty()) return getAppointments();
        return appointments.stream().filter(a ->
                a.getPatient().getName().toLowerCase().contains(q)
                || a.getDoctor().getName().toLowerCase().contains(q)
                || a.getStatus().toLowerCase().contains(q)
                || a.getAppointmentId().toLowerCase().contains(q)
        ).collect(Collectors.toList());
    }
}

// ─────────────────────────────────────────────
// UI Components
// ─────────────────────────────────────────────
class MBtn extends JButton {
    MBtn(String text, Color base) {
        super(text);
        setFont(new Font("Segoe UI", Font.BOLD, 13));
        setForeground(Color.WHITE);
        setBackground(base);
        setFocusPainted(false);
        setBorderPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setOpaque(true);
        Color hover   = base.brighter();
        Color pressed = base.darker();
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e)  { setBackground(hover); }
            @Override public void mouseExited(MouseEvent e)   { setBackground(base); }
            @Override public void mousePressed(MouseEvent e)  { setBackground(pressed); }
            @Override public void mouseReleased(MouseEvent e) { setBackground(hover); }
        });
    }
}

class MField extends JTextField {
    MField(int cols) {
        super(cols);
        setFont(new Font("Segoe UI", Font.PLAIN, 13));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200,200,210), 1),
                BorderFactory.createEmptyBorder(7,10,7,10)));
    }
}

// ─────────────────────────────────────────────
// Main GUI
// ─────────────────────────────────────────────
public class AppointMedGUI extends JFrame {

    // ── Palette ───────────────────────────────
    private final Color C_BLUE    = new Color(28,  78, 156);
    private final Color C_TEAL    = new Color(26, 188, 156);
    private final Color C_SKY     = new Color(52, 152, 219);
    private final Color C_RED     = new Color(192,  57,  43);
    private final Color C_BG      = new Color(244, 246, 251);
    private final Color C_CARD    = Color.WHITE;

    // ── Format ────────────────────────────────
    private final DateTimeFormatter FMT_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private final DateTimeFormatter FMT_TIME = DateTimeFormatter.ofPattern("H:mm");

    // ── Model ────────────────────────────────
    private final AppointmentScheduler scheduler = new AppointmentScheduler();

    // ── Shared widgets ────────────────────────
    private final JLabel statusLabel = new JLabel("Ready");

    // Register tab
    private MField regName, regContact, regAge, regHistory;

    // Book tab
    private JComboBox<Patient> bookPatient;
    private JComboBox<Doctor>  bookDoctor;
    private MField             bookDate, bookTime;
    private JList<String>      slotsPanel;
    private DefaultListModel<String> slotsModel;

    // Manage tab
    private JTable             aptTable;
    private DefaultTableModel  aptModel;
    private MField             searchField;

    // Doctor Schedule tab
    private JComboBox<Doctor>  schedDoctor;
    private MField             schedDate, schedTime;
    private JTextArea          schedDisplay;

    // ─────────────────────────────────────────
    public AppointMedGUI() {
        setTitle("AppointMed — Medical Appointment System");
        setSize(1150, 780);
        setMinimumSize(new Dimension(900, 600));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(C_BG);

        add(buildHeader(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabs.setBackground(C_BG);
        tabs.addTab("📋 Register Patient",   buildRegisterTab());
        tabs.addTab("👥 Patients",           buildPatientsTab());
        tabs.addTab("📅 Book Appointment",   buildBookTab());
        tabs.addTab("🗂 Manage",             buildManageTab());
        tabs.addTab("🩺 Doctor Schedules",   buildScheduleTab());
        add(tabs, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ── Header ────────────────────────────────
    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(C_BLUE);
        p.setBorder(BorderFactory.createEmptyBorder(14,20,14,20));
        JLabel title = new JLabel("🏥  AppointMed");
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));
        title.setForeground(Color.WHITE);
        JLabel sub = new JLabel("Medical Appointment Management System");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        sub.setForeground(new Color(180,210,255));
        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        left.add(title, BorderLayout.NORTH);
        left.add(sub,   BorderLayout.SOUTH);
        p.add(left, BorderLayout.WEST);
        return p;
    }

    // ── Status Bar ────────────────────────────
    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 5));
        bar.setBackground(new Color(220,224,235));
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        bar.add(statusLabel);
        return bar;
    }

    // ── Helpers ───────────────────────────────
    private JLabel lbl(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 13));
        l.setForeground(C_BLUE);
        return l;
    }
    private JPanel card() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(C_CARD);
        p.setBorder(new CompoundBorder(
                new LineBorder(new Color(215,220,235), 1, true),
                new EmptyBorder(18,18,18,18)));
        return p;
    }
    private void status(String msg) { statusLabel.setText(msg); }
    private void err(Component parent, String msg) {
        JOptionPane.showMessageDialog(parent, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }
    private void ok(Component parent, String msg) {
        JOptionPane.showMessageDialog(parent, msg, "Done", JOptionPane.INFORMATION_MESSAGE);
    }

    // ─────────────────────────────────────────
    // TAB 1: Register Patient
    // ─────────────────────────────────────────
    private JPanel buildRegisterTab() {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(C_BG);
        wrap.setBorder(new EmptyBorder(24,24,24,24));

        JPanel c = card();
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8,8,8,8);
        g.fill   = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Patient Registration");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(C_BLUE);
        g.gridx=0; g.gridy=0; g.gridwidth=2; c.add(title, g);

        g.gridwidth=1;
        regName = new MField(30); addRow(c, g, 1, "Full Name *", regName);
        regContact = new MField(30); addRow(c, g, 2, "Contact (XXX-XXXX) *", regContact);
        regAge  = new MField(30); addRow(c, g, 3, "Age *", regAge);
        regHistory = new MField(30); addRow(c, g, 4, "Medical History (optional)", regHistory);

        MBtn btn = new MBtn("Register Patient", C_TEAL);
        btn.setPreferredSize(new Dimension(200,38));
        btn.addActionListener(e -> doRegister());
        g.gridx=0; g.gridy=5; g.gridwidth=2; g.anchor=GridBagConstraints.CENTER;
        c.add(btn, g);

        wrap.add(c, BorderLayout.NORTH);
        return wrap;
    }

    private void addRow(JPanel p, GridBagConstraints g, int row, String label, JComponent field) {
        g.gridx=0; g.gridy=row; g.gridwidth=1; p.add(lbl(label), g);
        g.gridx=1; p.add(field, g);
    }

    private void doRegister() {
        String name    = regName.getText().trim();
        String contact = regContact.getText().trim();
        String ageStr  = regAge.getText().trim();
        String hist    = regHistory.getText().trim();
        try {
            scheduler.validatePatientData(name, contact, ageStr);
            Patient p = scheduler.addPatient(name, contact, Integer.parseInt(ageStr), hist);
            ok(this, "Registered: " + p.getPatientId() + " — " + p.getName());
            status("Registered: " + p.getName());
            regName.setText(""); regContact.setText(""); regAge.setText(""); regHistory.setText("");
            refreshBookPatientCombo();
        } catch (InvalidPatientDataException ex) { err(this, ex.getMessage()); }
    }

    // ─────────────────────────────────────────
    // TAB 2: Patients list
    // ─────────────────────────────────────────
    private JPanel buildPatientsTab() {
        JPanel wrap = new JPanel(new BorderLayout(0,10));
        wrap.setBackground(C_BG);
        wrap.setBorder(new EmptyBorder(16,16,16,16));

        String[] cols = {"ID","Name","Age","Contact","Medical History"};
        DefaultTableModel model = new DefaultTableModel(cols,0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(model);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(24);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));

        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(new LineBorder(new Color(215,220,235)));

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setBackground(C_BG);
        MBtn refresh = new MBtn("Refresh", C_SKY);
        refresh.addActionListener(e -> {
            model.setRowCount(0);
            for (Patient p : scheduler.getPatients())
                model.addRow(new Object[]{p.getPatientId(), p.getName(), p.getAge(),
                        p.getContactNumber(), p.getMedicalHistory()});
        });
        bottom.add(refresh);
        wrap.add(sp, BorderLayout.CENTER);
        wrap.add(bottom, BorderLayout.SOUTH);
        refresh.doClick();
        return wrap;
    }

    // ─────────────────────────────────────────
    // TAB 3: Book Appointment
    // ─────────────────────────────────────────
    private JPanel buildBookTab() {
        JPanel wrap = new JPanel(new BorderLayout(12,12));
        wrap.setBackground(C_BG);
        wrap.setBorder(new EmptyBorder(18,18,18,18));

        JPanel form = card();
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(9,9,9,9);
        g.fill   = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Book Appointment");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(C_BLUE);
        g.gridx=0; g.gridy=0; g.gridwidth=2; form.add(title, g);

        // Patient combo
        g.gridwidth=1; g.gridy=1;
        bookPatient = new JComboBox<>(); bookPatient.setPreferredSize(new Dimension(380,30));
        addRow(form, g, 1, "Patient", bookPatient);

        // Doctor combo
        bookDoctor = new JComboBox<>();
        for (Doctor d : scheduler.getDoctors()) bookDoctor.addItem(d);
        bookDoctor.setPreferredSize(new Dimension(380,30));
        addRow(form, g, 2, "Doctor", bookDoctor);

        // Date
        bookDate = new MField(20);
        addRow(form, g, 3, "Date (YYYY-MM-DD)", bookDate);

        // Time
        bookTime = new MField(20);
        addRow(form, g, 4, "Time (H:mm, 24h)", bookTime);

        // Available slots
        g.gridx=0; g.gridy=5; g.gridwidth=2;
        form.add(lbl("Available slots for selected doctor & date:"), g);
        slotsModel = new DefaultListModel<>();
        slotsPanel = new JList<>(slotsModel);
        slotsPanel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        slotsPanel.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        slotsPanel.setVisibleRowCount(2);
        slotsPanel.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && slotsPanel.getSelectedValue() != null)
                bookTime.setText(slotsPanel.getSelectedValue());
        });
        g.gridy=6; form.add(new JScrollPane(slotsPanel), g);

        MBtn showSlots = new MBtn("Show Available Slots", C_SKY);
        showSlots.addActionListener(e -> refreshSlots());
        g.gridy=7; g.anchor=GridBagConstraints.WEST; g.gridwidth=1;
        form.add(showSlots, g);

        MBtn bookBtn = new MBtn("Book Appointment", C_BLUE);
        bookBtn.setPreferredSize(new Dimension(200,38));
        bookBtn.addActionListener(e -> doBook());
        g.gridx=1; form.add(bookBtn, g);

        wrap.add(form, BorderLayout.CENTER);
        refreshBookPatientCombo();
        return wrap;
    }

    private void refreshBookPatientCombo() {
        DefaultComboBoxModel<Patient> m = new DefaultComboBoxModel<>();
        for (Patient p : scheduler.getPatients()) m.addElement(p);
        if (bookPatient != null) bookPatient.setModel(m);
    }

    private void refreshSlots() {
        Doctor doc = (Doctor) bookDoctor.getSelectedItem();
        String dateStr = bookDate.getText().trim();
        if (doc == null || dateStr.isEmpty()) { slotsModel.clear(); return; }
        try {
            LocalDate date = LocalDate.parse(dateStr, FMT_DATE);
            slotsModel.clear();
            for (LocalTime t : scheduler.getAvailableSlots(doc, date))
                slotsModel.addElement(t.toString());
            if (slotsModel.isEmpty()) slotsModel.addElement("No slots available");
        } catch (DateTimeParseException ex) {
            slotsModel.clear(); slotsModel.addElement("Invalid date format");
        }
    }

    private void doBook() {
        Patient p = (Patient) bookPatient.getSelectedItem();
        if (p == null) { err(this, "Please register or select a patient first."); return; }
        Doctor  d = (Doctor) bookDoctor.getSelectedItem();
        try {
            LocalDate date = LocalDate.parse(bookDate.getText().trim(), FMT_DATE);
            LocalTime time = LocalTime.parse(bookTime.getText().trim(), FMT_TIME);
            scheduler.validateAppointment(d, date, time, null);
            Appointment a = scheduler.bookAppointment(p, d, date, time);
            ok(this, "Appointment booked: " + a.getAppointmentId());
            status("Booked: " + a.getAppointmentId() + " for " + p.getName());
            bookDate.setText(""); bookTime.setText(""); slotsModel.clear();
            refreshAptTable(scheduler.getAppointments());
        } catch (DateTimeParseException ex) {
            err(this, "Invalid date/time format.\nDate: YYYY-MM-DD   Time: H:mm (e.g. 9:30)");
        } catch (InvalidAppointmentException | AppointmentConflictException ex) {
            err(this, ex.getMessage());
        }
    }

    // ─────────────────────────────────────────
    // TAB 4: Manage Appointments
    // ─────────────────────────────────────────
    private JPanel buildManageTab() {
        JPanel wrap = new JPanel(new BorderLayout(0,10));
        wrap.setBackground(C_BG);
        wrap.setBorder(new EmptyBorder(12,12,12,12));

        // Search bar
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        searchBar.setBackground(C_BG);
        searchField = new MField(30);
        searchBar.add(lbl("Search:"));
        searchBar.add(searchField);
        MBtn searchBtn = new MBtn("Search", C_SKY);
        searchBtn.addActionListener(e -> refreshAptTable(scheduler.search(searchField.getText())));
        MBtn clearBtn = new MBtn("Clear", new Color(140,140,150));
        clearBtn.addActionListener(e -> { searchField.setText(""); refreshAptTable(scheduler.getAppointments()); });
        searchBar.add(searchBtn);
        searchBar.add(clearBtn);

        // Table
        String[] cols = {"APT ID","Patient","Doctor","Specialization","Date","Time","Status","Notes"};
        aptModel = new DefaultTableModel(cols,0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        aptTable = new JTable(aptModel);
        aptTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        aptTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        aptTable.setRowHeight(24);
        aptTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));

        // Color-code rows by status
        aptTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v,
                    boolean sel, boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, v, sel, foc, row, col);
                if (!sel) {
                    String status = (String) aptModel.getValueAt(row, 6);
                    if ("Cancelled".equals(status))   c.setBackground(new Color(255,235,235));
                    else if ("Rescheduled".equals(status)) c.setBackground(new Color(255,250,220));
                    else                               c.setBackground(Color.WHITE);
                }
                return c;
            }
        });

        JScrollPane sp = new JScrollPane(aptTable);
        sp.setBorder(new LineBorder(new Color(215,220,235)));

        // Buttons
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT,8,6));
        btns.setBackground(C_BG);
        MBtn refreshBtn  = new MBtn("Refresh",             C_SKY);
        MBtn updateBtn   = new MBtn("Reschedule / Update", C_BLUE);
        MBtn cancelBtn   = new MBtn("Cancel Appointment",  C_RED);
        refreshBtn.addActionListener(e -> refreshAptTable(scheduler.getAppointments()));
        updateBtn.addActionListener(e -> openUpdateDialog());
        cancelBtn.addActionListener(e -> doCancel());
        btns.add(refreshBtn); btns.add(updateBtn); btns.add(cancelBtn);

        wrap.add(searchBar, BorderLayout.NORTH);
        wrap.add(sp,        BorderLayout.CENTER);
        wrap.add(btns,      BorderLayout.SOUTH);

        refreshAptTable(scheduler.getAppointments());
        return wrap;
    }

    private void refreshAptTable(List<Appointment> list) {
        aptModel.setRowCount(0);
        for (Appointment a : list)
            aptModel.addRow(new Object[]{
                    a.getAppointmentId(),
                    a.getPatient().getName(),
                    "Dr. " + a.getDoctor().getName(),
                    a.getDoctor().getSpecialization(),
                    a.getDate().toString(),
                    a.getTime().toString(),
                    a.getStatus(),
                    a.getNotes()
            });
        status("Showing " + list.size() + " appointment(s).");
    }

    private Appointment selectedApt() {
        int row = aptTable.getSelectedRow();
        if (row < 0) return null;
        return scheduler.findById((String) aptModel.getValueAt(row, 0));
    }

    private void doCancel() {
        Appointment a = selectedApt();
        if (a == null) { err(this, "Select an appointment first."); return; }
        if ("Cancelled".equals(a.getStatus())) { err(this, "This appointment is already cancelled."); return; }
        int c = JOptionPane.showConfirmDialog(this,
                "Cancel " + a.getAppointmentId() + " for " + a.getPatient().getName() + "?",
                "Confirm Cancellation", JOptionPane.YES_NO_OPTION);
        if (c == JOptionPane.YES_OPTION) {
            scheduler.cancelAppointment(a);
            refreshAptTable(scheduler.getAppointments());
            status("Cancelled: " + a.getAppointmentId());
        }
    }

    private void openUpdateDialog() {
        Appointment a = selectedApt();
        if (a == null) { err(this, "Select an appointment first."); return; }
        if ("Cancelled".equals(a.getStatus())) { err(this, "Cannot reschedule a cancelled appointment."); return; }

        JDialog dlg = new JDialog(this, "Update — " + a.getAppointmentId(), true);
        dlg.setSize(520, 380);
        dlg.setLocationRelativeTo(this);

        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new EmptyBorder(16,16,16,16));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(9,9,9,9);
        g.fill   = GridBagConstraints.HORIZONTAL;

        JComboBox<Doctor> docBox = new JComboBox<>();
        for (Doctor d : scheduler.getDoctors()) docBox.addItem(d);
        docBox.setSelectedItem(a.getDoctor());
        addRow(p, g, 0, "Doctor", docBox);

        MField dateF = new MField(14); dateF.setText(a.getDate().toString());
        addRow(p, g, 1, "Date (YYYY-MM-DD)", dateF);

        MField timeF = new MField(14); timeF.setText(a.getTime().toString());
        addRow(p, g, 2, "Time (H:mm)", timeF);

        MField notesF = new MField(24); notesF.setText(a.getNotes());
        addRow(p, g, 3, "Notes", notesF);

        g.gridx=0; g.gridy=4;
        p.add(new JLabel("Status: " + a.getStatus()), g);

        MBtn save = new MBtn("Save Changes", C_TEAL);
        save.addActionListener(ev -> {
            try {
                Doctor nd = (Doctor) docBox.getSelectedItem();
                LocalDate nd2 = LocalDate.parse(dateF.getText().trim(), FMT_DATE);
                LocalTime nt  = LocalTime.parse(timeF.getText().trim(), FMT_TIME);
                scheduler.updateAppointment(a, nd, nd2, nt, notesF.getText().trim());
                dlg.dispose();
                refreshAptTable(scheduler.getAppointments());
                status("Updated: " + a.getAppointmentId());
                ok(this, "Appointment updated successfully.");
            } catch (DateTimeParseException ex) {
                err(dlg, "Invalid date/time format.");
            } catch (InvalidAppointmentException | AppointmentConflictException ex) {
                err(dlg, ex.getMessage());
            }
        });
        g.gridx=1; g.gridy=4; p.add(save, g);

        dlg.getContentPane().add(p);
        dlg.setVisible(true);
    }

    // ─────────────────────────────────────────
    // TAB 5: Doctor Schedules
    // ─────────────────────────────────────────
    private JPanel buildScheduleTab() {
        JPanel wrap = new JPanel(new BorderLayout(0,10));
        wrap.setBackground(C_BG);
        wrap.setBorder(new EmptyBorder(14,14,14,14));

        JPanel top = card();
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8,8,8,8);
        g.fill   = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Doctor Schedule & Availability");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(C_BLUE);
        g.gridx=0; g.gridy=0; g.gridwidth=3; top.add(title, g);

        schedDoctor = new JComboBox<>();
        for (Doctor d : scheduler.getDoctors()) schedDoctor.addItem(d);
        schedDoctor.setPreferredSize(new Dimension(340,28));
        g.gridwidth=1; g.gridy=1;
        top.add(lbl("Doctor"), g);
        g.gridx=1; g.gridwidth=2; top.add(schedDoctor, g);

        schedDate = new MField(12);
        g.gridwidth=1; g.gridy=2; g.gridx=0; top.add(lbl("Date (YYYY-MM-DD)"), g);
        g.gridx=1; top.add(schedDate, g);
        MBtn blockDate = new MBtn("Block Full Day", new Color(180,60,60));
        g.gridx=2; top.add(blockDate, g);
        blockDate.addActionListener(e -> doBlockDate());

        schedTime = new MField(12);
        g.gridy=3; g.gridx=0; top.add(lbl("Time slot (H:mm) on date above"), g);
        g.gridx=1; top.add(schedTime, g);
        MBtn blockTime = new MBtn("Block Time Slot", new Color(200,100,20));
        g.gridx=2; top.add(blockTime, g);
        blockTime.addActionListener(e -> doBlockTime());

        schedDisplay = new JTextArea(14, 50);
        schedDisplay.setEditable(false);
        schedDisplay.setFont(new Font("Monospaced", Font.PLAIN, 13));
        schedDisplay.setBackground(new Color(250,252,255));
        JScrollPane sp = new JScrollPane(schedDisplay);
        sp.setBorder(new LineBorder(new Color(215,220,235)));

        MBtn view = new MBtn("View Schedule", C_BLUE);
        view.addActionListener(e -> doViewSchedule());
        JPanel bot = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bot.setBackground(C_BG); bot.add(view);

        wrap.add(top, BorderLayout.NORTH);
        wrap.add(sp,  BorderLayout.CENTER);
        wrap.add(bot, BorderLayout.SOUTH);
        return wrap;
    }

    private void doBlockDate() {
        Doctor d = (Doctor) schedDoctor.getSelectedItem();
        if (d == null) { err(this, "Select a doctor first."); return; }
        try {
            LocalDate dt = LocalDate.parse(schedDate.getText().trim(), FMT_DATE);
            d.addUnavailableDate(dt);
            scheduler.save();
            ok(this, "Blocked " + dt + " for " + d.getName());
            status("Blocked " + dt + " for " + d.getName());
            doViewSchedule();
        } catch (DateTimeParseException ex) { err(this, "Invalid date format (YYYY-MM-DD)."); }
    }

    private void doBlockTime() {
        Doctor d = (Doctor) schedDoctor.getSelectedItem();
        if (d == null) { err(this, "Select a doctor first."); return; }
        try {
            LocalDate dt = LocalDate.parse(schedDate.getText().trim(), FMT_DATE);
            LocalTime lt = LocalTime.parse(schedTime.getText().trim(), FMT_TIME);
            d.addUnavailableTime(dt, lt);
            scheduler.save();
            ok(this, "Blocked " + lt + " on " + dt + " for " + d.getName());
            doViewSchedule();
        } catch (DateTimeParseException ex) { err(this, "Invalid date/time format."); }
    }

    private void doViewSchedule() {
        Doctor d = (Doctor) schedDoctor.getSelectedItem();
        if (d == null) { schedDisplay.setText("No doctor selected."); return; }
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append("  ").append(d).append("\n");
        sb.append("  Contact: ").append(d.getContactNumber()).append("\n");
        sb.append("═══════════════════════════════════════\n\n");

        // Blocked full days
        List<LocalDate> blocked = new ArrayList<>(d.getUnavailableDates());
        Collections.sort(blocked);
        sb.append("🚫 Blocked Full Days:\n");
        if (blocked.isEmpty()) sb.append("   (none)\n");
        else blocked.forEach(dt -> sb.append("   ").append(dt).append("\n"));

        // Blocked time slots
        sb.append("\n⏰ Blocked Time Slots (next 90 days):\n");
        boolean any = false;
        for (int i = 0; i < 90; i++) {
            LocalDate dt = LocalDate.now().plusDays(i);
            List<LocalTime> times = d.getUnavailableTimes(dt);
            if (!times.isEmpty()) {
                any = true;
                sb.append("   ").append(dt).append(": ");
                times.stream().sorted().map(LocalTime::toString)
                     .forEach(s -> sb.append(s).append("  "));
                sb.append("\n");
            }
        }
        if (!any) sb.append("   (none in next 90 days)\n");

        // Upcoming appointments
        sb.append("\n📅 Upcoming Scheduled Appointments:\n");
        long count = scheduler.getAppointments().stream()
                .filter(a -> a.getDoctor().getDoctorId().equals(d.getDoctorId())
                          && !a.getStatus().equals("Cancelled")
                          && !a.getDate().isBefore(LocalDate.now()))
                .sorted(Comparator.comparing(Appointment::getDate).thenComparing(Appointment::getTime))
                .peek(a -> sb.append(String.format("   [%s] %s %s — %s\n",
                        a.getAppointmentId(), a.getDate(), a.getTime(), a.getPatient().getName())))
                .count();
        if (count == 0) sb.append("   (none)\n");

        schedDisplay.setText(sb.toString());
        status("Loaded schedule for " + d.getName());
    }

    // ─────────────────────────────────────────
    // Entry Point
    // ─────────────────────────────────────────
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(AppointMedGUI::new);
    }
}