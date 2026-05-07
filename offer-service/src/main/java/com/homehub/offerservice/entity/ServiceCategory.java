package com.homehub.offerservice.entity;

import jakarta.persistence.*;

/**
 * Represents a service trade category such as Plumbing or Carpentry.
 * Categories are managed by admins and referenced by service listings.
 */
@Entity
@Table(name = "service_categories")
public class ServiceCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long categoryId;

    @Column(nullable = false, unique = true)
    private String name;

    public ServiceCategory() {}

    public ServiceCategory(String name) {
        this.name = name;
    }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
