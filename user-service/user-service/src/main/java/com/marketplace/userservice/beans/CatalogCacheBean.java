package com.marketplace.userservice.beans;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import java.util.ArrayList;
import java.util.List;

/*
 * @Singleton means:
 * - Only ONE instance of this class exists in the entire app
 * - Every request shares the SAME instance
 * - Like a shared whiteboard everyone reads from
 *
 * @Startup means:
 * - JBoss creates this bean immediately when the app starts
 * - It doesn't wait for someone to call it first
 */
@Singleton
@Startup
public class CatalogCacheBean {

    /*
     * This list lives in memory as long as the app is running.
     * Since this is a Singleton, everyone shares this same list.
     */
    private List<String> categories = new ArrayList<>();

    /*
     * @PostConstruct means:
     * - Run this method automatically right after JBoss creates the bean
     * - Perfect place to load initial data
     */
    @PostConstruct
    public void init() {
        // Pre-load the service categories when app starts
        categories.add("Plumbing");
        categories.add("Carpentry");
        categories.add("Electrical");
        categories.add("Cleaning");
        categories.add("Painting");

        System.out.println("CatalogCacheBean started! " +
                "Categories loaded: " + categories);
    }

    /*
     * Anyone can call this to get all categories.
     * Returns the same shared list every time.
     */
    public List<String> getCategories() {
        return categories;
    }

    /*
     * Admin can add a new category at runtime.
     * Because this is Singleton, the new category is
     * instantly visible to everyone in the app.
     */
    public void addCategory(String category) {
        if (!categories.contains(category)) {
            categories.add(category);
            System.out.println("New category added: " + category);
        }
    }
}