package com.marketplace.userservice.entities;

import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonbProperty("userId")  // renames "id" to "userId" in JSON response
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role;

    @JsonbProperty("professionType")  // renames "profession" to "professionType"
    private String profession;

    // createdAt — contract requires this field
    @JsonbProperty("createdAt")
    private String createdAt;

    // Used only during registration — not stored in DB
    @jakarta.persistence.Transient
    private Double initialBalance;

    public User() {}

    public User(String username, String password,
                String role, String profession) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.profession = profession;
        // Set timestamp when user is created
        this.createdAt = LocalDateTime.now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z";
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getProfession() { return profession; }
    public void setProfession(String profession) { this.profession = profession; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public Double getInitialBalance() { return initialBalance; }
    public void setInitialBalance(Double initialBalance) { this.initialBalance = initialBalance; }
}