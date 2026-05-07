package com.marketplace.userservice.beans;

import com.marketplace.userservice.entities.User;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.List;

/*
 * @Stateless means:
 * - This bean has NO memory between calls
 * - Every time someone calls register() or login(),
 *   JBoss gives them a fresh instance
 * - Like a calculator: does the job, forgets everything after
 */
@Stateless
public class AuthBean {

    /*
     * @PersistenceContext injects the database connection automatically.
     * "UsersPU" must match the name in persistence.xml
     * We don't create this manually — JBoss gives it to us!
     */
    @PersistenceContext(unitName = "UsersPU")
    private EntityManager em;

    /*
     * REGISTER a new user.
     * EntityManager.persist() = INSERT INTO users in MySQL
     */
    public User register(String username, String password,
                         String role, String profession) {

        // Check if username already exists
        long count = em.createQuery(
                        "SELECT COUNT(u) FROM User u WHERE u.username = :username",
                        Long.class)
                .setParameter("username", username)
                .getSingleResult();

        if (count > 0) {
            // Username taken — return null to signal failure
            return null;
        }

        // Create new user and save to database
        User user = new User(username, password, role, profession);
        em.persist(user); // this runs INSERT INTO users(...)
        return user;
    }

    /*
     * LOGIN — check username + password match.
     * Returns the User if correct, null if wrong.
     */
    public User login(String username, String password) {

        TypedQuery<User> query = em.createQuery(
                "SELECT u FROM User u WHERE u.username = :username AND u.password = :password",
                User.class);

        query.setParameter("username", username);
        query.setParameter("password", password);

        List<User> results = query.getResultList();

        if (results.isEmpty()) {
            return null; // wrong username or password
        }
        return results.get(0); // login success
    }

    /*
     * GET a user by their ID.
     * EntityManager.find() = SELECT * FROM users WHERE id = ?
     */
    public User getById(Long id) {
        return em.find(User.class, id);
    }

    /*
     * GET ALL users — Admin uses this.
     */
    public List<User> getAllUsers() {
        return em.createQuery("SELECT u FROM User u", User.class)
                .getResultList();
    }
}