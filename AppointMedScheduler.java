import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

class InvalidAppointmentException extends Exception {
    public InvalidAppointmentException(String message) {
        super(message);
    }
}

class DoctorNotFoundException extends Exception {
    public DoctorNotFoundException(String message) {
        super(message);
    }
}

class TimeSlotUnavailableException extends Exception {
    public TimeSlotUnavailableException(String message) {
        super(message);
    }
}

// Generic class for managing collections
class DataManager<T> {
    private ArrayList<T> items;
    
    public DataManager() {
        this.items = new ArrayList<>();
    }
    
    public void add(T item) {
        items.add(item);
    }
    
    public void remove(T item) {
        items.remove(item);
    }
    
    public ArrayList<T> getAll() {
        return new ArrayList<>(items);
    }
    
    public int size() {
        return items.size();
    }
    
    public T get(int index) {
        return items.get(index);
    }
}

// Base Person class demonstrating inheritance
abstract class Person {
    protected String id;
    protected String name;
    protected String phone;
    protected String email;
    
    public Person(String id, String name, String phone, String email) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.email = email;
    }
    
    public String getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    
    public abstract String getDetails();
}

// Patient class
class Patient extends Person {
    private String dateOfBirth;
    private String medicalHistory;
    
    public Patient(String id, String name, String phone, String email, String dob) {
        super(id, name, phone, email);
        this.dateOfBirth = dob;
        this.medicalHistory = "";
    }
    
    @Override
    public String getDetails() {
        return "Patient: " + name + " (ID: " + id + ")";
    }
    
    public String getDateOfBirth() { return dateOfBirth; }
}

// Doctor class
class Doctor extends Person {
    private String specialization;
    private ArrayList<String> availableDays;
    
    public Doctor(String id, String name, String phone, String email, String specialization) {
        super(id, name, phone, email);
        this.specialization = specialization;
        this.availableDays = new ArrayList<>();
    }
    
    @Override
    public String getDetails() {
        return name + " - " + specialization;
    }
    
    public String getSpecialization() { return specialization; }
    
    @Override
    public String toString() {
        return getDetails();
    }
}

// Appointment class
class Appointment {
    private String appointmentId;
    private Patient patient;
    private Doctor doctor;
    private String date;
    private String time;
    private String status;
    private String notes;
    
    public Appointment(String id, Patient patient, Doctor doctor, String date, String time) {
        this.appointmentId = id;
        this.patient = patient;
        this.doctor = doctor;
        this.date = date;
        this.time = time;
        this.status = "Scheduled";
        this.notes = "";
    }
    
    public String getAppointmentId() { return appointmentId; }
    public Patient getPatient() { return patient; }
    public Doctor getDoctor() { return doctor; }
    public String getDate() { return date; }
    public String getTime() { return time; }
    public String getStatus() { return status; }
    
    public void setStatus(String status) { this.status = status; }
    public void setNotes(String notes) { this.notes = notes; }
    
    @Override
    public String toString() {
        return date + " at " + time + " - Dr. " + doctor.getName() + " with " + patient.getName();
    }
}


public class AppointMedScheduler extends JFrame {
    // Collections to store data
    private DataManager<Patient> patientManager;
    private DataManager<Doctor> doctorManager;
    private DataManager<Appointment> appointmentManager;
    private HashMap<String, Doctor> doctorMap;
    
    // GUI Components
    private JTabbedPane tabbedPane;
    private DefaultTableModel appointmentTableModel;
    private JTable appointmentTable;
    
    public AppointMedScheduler() {
        // Initialize data structures
        patientManager = new DataManager<>();
        doctorManager = new DataManager<>();
        appointmentManager = new DataManager<>();
        doctorMap = new HashMap<>();
        
        // Initialize sample data
        initializeSampleData();
        
        // Setup GUI
        setupGUI();
    }
    
    private void initializeSampleData() {
        // Add sample doctors
        Doctor doc1 = new Doctor("D001", "Dr. Sarah Johnson", "555-0101", "sarah.j@hospital.com", "Cardiology");
        Doctor doc2 = new Doctor("D002", "Dr. Michael Chen", "555-0102", "michael.c@hospital.com", "Pediatrics");
        Doctor doc3 = new Doctor("D003", "Dr. Emily Rodriguez", "555-0103", "emily.r@hospital.com", "General Practice");
        
        doctorManager.add(doc1);
        doctorManager.add(doc2);
        doctorManager.add(doc3);
        
        doctorMap.put(doc1.getId(), doc1);
        doctorMap.put(doc2.getId(), doc2);
        doctorMap.put(doc3.getId(), doc3);
        
        // Add sample patients
        Patient pat1 = new Patient("P001", "John Smith", "555-1001", "john.s@email.com", "1985-05-15");
        Patient pat2 = new Patient("P002", "Maria Garcia", "555-1002", "maria.g@email.com", "1990-08-22");
        
        patientManager.add(pat1);
        patientManager.add(pat2);
    }
    
    private void setupGUI() {
        setTitle("AppointMed: Medical Appointment Scheduler");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        // Create tabbed pane
        tabbedPane = new JTabbedPane();
        
        // Add tabs
        tabbedPane.addTab("Dashboard", createDashboardPanel());
        tabbedPane.addTab("Book Appointment", createBookingPanel());
        tabbedPane.addTab("Manage Appointments", createManagePanel());
        tabbedPane.addTab("Patients", createPatientPanel());
        tabbedPane.addTab("Doctors", createDoctorPanel());
        
        add(tabbedPane);
    }
    
    private JPanel createDashboardPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // Title
        JLabel titleLabel = new JLabel("AppointMed Dashboard", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 28));
        titleLabel.setForeground(new Color(41, 128, 185));
        panel.add(titleLabel, BorderLayout.NORTH);
        
        // Statistics panel
        JPanel statsPanel = new JPanel(new GridLayout(2, 2, 20, 20));
        statsPanel.setBorder(BorderFactory.createEmptyBorder(30, 50, 30, 50));
        
        statsPanel.add(createStatCard("Total Appointments", String.valueOf(appointmentManager.size()), new Color(52, 152, 219)));
        statsPanel.add(createStatCard("Total Doctors", String.valueOf(doctorManager.size()), new Color(46, 204, 113)));
        statsPanel.add(createStatCard("Total Patients", String.valueOf(patientManager.size()), new Color(155, 89, 182)));
        statsPanel.add(createStatCard("Scheduled Today", "0", new Color(241, 196, 15)));
        
        panel.add(statsPanel, BorderLayout.CENTER);
        
        // Welcome message
        JLabel welcomeLabel = new JLabel("<html><center>Welcome to AppointMed!<br>Streamlining medical appointments for better healthcare.</center></html>", SwingConstants.CENTER);
        welcomeLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        panel.add(welcomeLabel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createStatCard(String title, String value, Color color) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(color);
        card.setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));
        
        JLabel valueLabel = new JLabel(value, SwingConstants.CENTER);
        valueLabel.setFont(new Font("Arial", Font.BOLD, 36));
        valueLabel.setForeground(Color.WHITE);
        
        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        titleLabel.setForeground(Color.WHITE);
        
        card.add(valueLabel, BorderLayout.CENTER);
        card.add(titleLabel, BorderLayout.SOUTH);
        
        return card;
    }
    
    private JPanel createBookingPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // Title
        JLabel titleLabel = new JLabel("Book New Appointment", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        panel.add(titleLabel, BorderLayout.NORTH);
        
        // Form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Patient selection
        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("Select Patient:"), gbc);
        
        JComboBox<Patient> patientCombo = new JComboBox<>();
        for (Patient p : patientManager.getAll()) {
            patientCombo.addItem(p);
        }
        patientCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Patient) {
                    setText(((Patient) value).getName());
                }
                return this;
            }
        });
        gbc.gridx = 1;
        formPanel.add(patientCombo, gbc);
        
        // Doctor selection
        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("Select Doctor:"), gbc);
        
        JComboBox<Doctor> doctorCombo = new JComboBox<>();
        for (Doctor d : doctorManager.getAll()) {
            doctorCombo.addItem(d);
        }
        gbc.gridx = 1;
        formPanel.add(doctorCombo, gbc);
        
        // Date field
        gbc.gridx = 0; gbc.gridy = 2;
        formPanel.add(new JLabel("Date (YYYY-MM-DD):"), gbc);
        
        JTextField dateField = new JTextField(15);
        gbc.gridx = 1;
        formPanel.add(dateField, gbc);
        
        // Time selection
        gbc.gridx = 0; gbc.gridy = 3;
        formPanel.add(new JLabel("Time:"), gbc);
        
        String[] times = {"09:00", "10:00", "11:00", "13:00", "14:00", "15:00", "16:00"};
        JComboBox<String> timeCombo = new JComboBox<>(times);
        gbc.gridx = 1;
        formPanel.add(timeCombo, gbc);
        
        // Book button
        JButton bookButton = new JButton("Book Appointment");
        bookButton.setBackground(new Color(52, 152, 219));
        bookButton.setForeground(Color.WHITE);
        bookButton.setFont(new Font("Arial", Font.BOLD, 14));
        bookButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    // Validation using control statements
                    if (patientCombo.getSelectedItem() == null) {
                        throw new InvalidAppointmentException("Please select a patient");
                    }
                    if (doctorCombo.getSelectedItem() == null) {
                        throw new DoctorNotFoundException("Please select a doctor");
                    }
                    if (dateField.getText().trim().isEmpty()) {
                        throw new InvalidAppointmentException("Please enter a date");
                    }
                    
                    // Check for conflicts using control statements
                    Patient selectedPatient = (Patient) patientCombo.getSelectedItem();
                    Doctor selectedDoctor = (Doctor) doctorCombo.getSelectedItem();
                    String date = dateField.getText().trim();
                    String time = (String) timeCombo.getSelectedItem();
                    
                    for (Appointment apt : appointmentManager.getAll()) {
                        if (apt.getDoctor().getId().equals(selectedDoctor.getId()) &&
                            apt.getDate().equals(date) && apt.getTime().equals(time)) {
                            throw new TimeSlotUnavailableException("This time slot is already booked for this doctor");
                        }
                    }
                    
                    // Create appointment
                    String aptId = "APT" + String.format("%03d", appointmentManager.size() + 1);
                    Appointment newApt = new Appointment(aptId, selectedPatient, selectedDoctor, date, time);
                    appointmentManager.add(newApt);
                    
                    // Update table
                    updateAppointmentTable();
                    
                    JOptionPane.showMessageDialog(panel, 
                        "Appointment booked successfully!\nAppointment ID: " + aptId,
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                    
                    // Clear form
                    dateField.setText("");
                    patientCombo.setSelectedIndex(0);
                    doctorCombo.setSelectedIndex(0);
                    timeCombo.setSelectedIndex(0);
                    
                } catch (InvalidAppointmentException | DoctorNotFoundException | TimeSlotUnavailableException ex) {
                    JOptionPane.showMessageDialog(panel, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        gbc.insets = new Insets(20, 10, 10, 10);
        formPanel.add(bookButton, gbc);
        
        panel.add(formPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createManagePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // Title
        JLabel titleLabel = new JLabel("Manage Appointments", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        panel.add(titleLabel, BorderLayout.NORTH);
        
        // Table
        String[] columns = {"Appointment ID", "Patient", "Doctor", "Date", "Time", "Status"};
        appointmentTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        appointmentTable = new JTable(appointmentTableModel);
        appointmentTable.setRowHeight(25);
        appointmentTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
        
        JScrollPane scrollPane = new JScrollPane(appointmentTable);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        
        JButton cancelButton = new JButton("Cancel Selected");
        cancelButton.setBackground(new Color(231, 76, 60));
        cancelButton.setForeground(Color.WHITE);
        cancelButton.addActionListener(e -> cancelAppointment());
        
        JButton refreshButton = new JButton("Refresh");
        refreshButton.setBackground(new Color(52, 152, 219));
        refreshButton.setForeground(Color.WHITE);
        refreshButton.addActionListener(e -> updateAppointmentTable());
        
        buttonPanel.add(cancelButton);
        buttonPanel.add(refreshButton);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        // Initial load
        updateAppointmentTable();
        
        return panel;
    }
    
    private void updateAppointmentTable() {
        appointmentTableModel.setRowCount(0);
        for (Appointment apt : appointmentManager.getAll()) {
            appointmentTableModel.addRow(new Object[] {
                apt.getAppointmentId(),
                apt.getPatient().getName(),
                apt.getDoctor().getName(),
                apt.getDate(),
                apt.getTime(),
                apt.getStatus()
            });
        }
    }
    
    private void cancelAppointment() {
        int selectedRow = appointmentTable.getSelectedRow();
        if (selectedRow >= 0) {
            int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to cancel this appointment?",
                "Confirm Cancellation", JOptionPane.YES_NO_OPTION);
            
            if (confirm == JOptionPane.YES_OPTION) {
                Appointment apt = appointmentManager.get(selectedRow);
                apt.setStatus("Cancelled");
                updateAppointmentTable();
                JOptionPane.showMessageDialog(this, "Appointment cancelled successfully!");
            }
        } else {
            JOptionPane.showMessageDialog(this, "Please select an appointment to cancel!");
        }
    }
    
    private JPanel createPatientPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JLabel titleLabel = new JLabel("Patient Management", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        panel.add(titleLabel, BorderLayout.NORTH);
        
        // Patient list
        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (Patient p : patientManager.getAll()) {
            listModel.addElement(p.getName() + " (ID: " + p.getId() + ")");
        }
        
        JList<String> patientList = new JList<>(listModel);
        patientList.setFont(new Font("Arial", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(patientList);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Add patient button
        JButton addButton = new JButton("Add New Patient");
        addButton.setBackground(new Color(46, 204, 113));
        addButton.setForeground(Color.WHITE);
        addButton.addActionListener(e -> showAddPatientDialog(listModel));
        panel.add(addButton, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private void showAddPatientDialog(DefaultListModel<String> listModel) {
        JPanel dialogPanel = new JPanel(new GridLayout(5, 2, 10, 10));
        
        JTextField nameField = new JTextField();
        JTextField phoneField = new JTextField();
        JTextField emailField = new JTextField();
        JTextField dobField = new JTextField();
        
        dialogPanel.add(new JLabel("Name:"));
        dialogPanel.add(nameField);
        dialogPanel.add(new JLabel("Phone:"));
        dialogPanel.add(phoneField);
        dialogPanel.add(new JLabel("Email:"));
        dialogPanel.add(emailField);
        dialogPanel.add(new JLabel("Date of Birth:"));
        dialogPanel.add(dobField);
        
        int result = JOptionPane.showConfirmDialog(this, dialogPanel, "Add New Patient", JOptionPane.OK_CANCEL_OPTION);
        
        if (result == JOptionPane.OK_OPTION) {
            try {
                if (nameField.getText().trim().isEmpty()) {
                    throw new InvalidAppointmentException("Name cannot be empty");
                }
                
                String patientId = "P" + String.format("%03d", patientManager.size() + 1);
                Patient newPatient = new Patient(patientId, nameField.getText(), 
                    phoneField.getText(), emailField.getText(), dobField.getText());
                
                patientManager.add(newPatient);
                listModel.addElement(newPatient.getName() + " (ID: " + patientId + ")");
                
                JOptionPane.showMessageDialog(this, "Patient added successfully!");
            } catch (InvalidAppointmentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private JPanel createDoctorPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JLabel titleLabel = new JLabel("Doctor Management", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        panel.add(titleLabel, BorderLayout.NORTH);
        
        // Doctor list
        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (Doctor d : doctorManager.getAll()) {
            listModel.addElement(d.getDetails());
        }
        
        JList<String> doctorList = new JList<>(listModel);
        doctorList.setFont(new Font("Arial", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(doctorList);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            AppointMedScheduler app = new AppointMedScheduler();
            app.setVisible(true);
        });
    }
}