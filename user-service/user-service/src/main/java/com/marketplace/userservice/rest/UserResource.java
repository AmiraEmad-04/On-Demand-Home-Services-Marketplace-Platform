package com.marketplace.userservice.rest;

import com.marketplace.userservice.beans.AuthBean;
import com.marketplace.userservice.beans.CatalogCacheBean;
import com.marketplace.userservice.beans.ServiceRequestBean;
import com.marketplace.userservice.entities.User;
import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import com.marketplace.userservice.entities.ServiceRequest;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    @EJB
    private AuthBean authBean;

    @EJB
    private CatalogCacheBean catalogBean;

    @EJB
    private ServiceRequestBean serviceRequestBean;

    // ==========================================
    // POST /users/register
    // Body: { "username":"ali", "password":"123",
    //         "role":"CUSTOMER", "profession":null,
    //         "initialBalance": 200.0 }
    // ==========================================
    @POST
    @Path("/register")
    public Response register(User user) {

        User created = authBean.register(
                user.getUsername(),
                user.getPassword(),
                user.getRole(),
                user.getProfession()
        );

        if (created == null) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("{\"message\":\"Username already taken\"}")
                    .build();
        }

        /*
         * If the user is a CUSTOMER, automatically create their wallet.
         * We call Wallet Service via HTTP right after saving the user.
         * initialBalance comes from the request body (default 0.0 if not provided)
         */
        if ("CUSTOMER".equalsIgnoreCase(created.getRole())) {
            double initialBalance = user.getInitialBalance() != null
                    ? user.getInitialBalance() : 0.0;
            createWalletForUser(created.getId(), initialBalance);
        }

        return Response.status(Response.Status.CREATED)
                .entity(created)
                .build();
    }

    /*
     * Calls Wallet Service to create a wallet for the new user.
     * This is a simple HTTP POST — no libraries needed, just Java's built-in HttpURLConnection.
     */
    private void createWalletForUser(Long userId, double initialBalance) {
        try {
            // Wallet Service runs on port 8082
            URL url = new URL("http://localhost:8082/wallet-service/api/wallet/"
                    + userId + "/create");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // Send { "initialBalance": 200.0 }
            String body = "{\"initialBalance\": " + initialBalance + "}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes());
            }

            int responseCode = conn.getResponseCode();
            System.out.println("Wallet created for userId " + userId
                    + " | HTTP " + responseCode
                    + " | balance: " + initialBalance);

            conn.disconnect();

        } catch (Exception e) {
            // Log the error but don't fail registration if wallet service is down
            System.err.println("Could not create wallet for userId "
                    + userId + ": " + e.getMessage());
        }
    }

    // ==========================================
    // POST /users/login
    // Body: { "username":"ali", "password":"123" }
    // ==========================================
    @POST
    @Path("/login")
    public Response login(User user) {

        User found = authBean.login(
                user.getUsername(),
                user.getPassword()
        );

        if (found == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"message\":\"Wrong username or password\"}")
                    .build();
        }

        return Response.ok(found).build();
    }

    // ==========================================
    // GET /users/{id}/profile
    // ==========================================
    @GET
    @Path("/{id}/profile")
    public Response getProfile(@PathParam("id") Long id) {

        User user = authBean.getById(id);

        if (user == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"message\":\"User not found\"}")
                    .build();
        }

        return Response.ok(user).build();
    }

    // ==========================================
    // GET /users
    // Returns all users — Admin uses this
    // ==========================================
    @GET
    public Response getAllUsers() {
        List<User> users = authBean.getAllUsers();
        return Response.ok(users).build();
    }

    // ==========================================
    // GET /users/categories
    // ==========================================
    @GET
    @Path("/categories")
    public Response getCategories() {
        List<String> categories = catalogBean.getCategories();
        return Response.ok(categories).build();
    }

    // ==========================================
    // POST /users/categories
    // Body: { "category": "Gardening" }
    // ==========================================
    @POST
    @Path("/categories")
    public Response addCategory(CategoryRequest request) {
        catalogBean.addCategory(request.getCategory());
        return Response.ok(
                "{\"message\":\"Category added: " + request.getCategory() + "\"}"
        ).build();
    }

    // ==========================================
// POST /users/requests
// Customer submits a service request
// Body: { "customerId":1, "category":"Plumbing",
//         "maxPrice":100.0, "requiredDate":"2026-05-10" }
// ==========================================
    @POST
    @Path("/requests")
    public Response createRequest(ServiceRequest request) {

        if (request.getCustomerId() == null || request.getCategory() == null
                || request.getMaxPrice() == null || request.getRequiredDate() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"customerId, category, maxPrice, requiredDate are required\"}")
                    .build();
        }

        ServiceRequest created = serviceRequestBean.createRequest(
                request.getCustomerId(),
                request.getCategory(),
                request.getMaxPrice(),
                request.getRequiredDate()
        );

        return Response.status(Response.Status.CREATED)
                .entity(created)
                .build();
    }

    // ==========================================
// GET /users/requests/{customerId}
// Customer views their service requests
// ==========================================
    @GET
    @Path("/requests/{customerId}")
    public Response getRequests(@PathParam("customerId") Long customerId) {
        List<ServiceRequest> requests =
                serviceRequestBean.getRequestsByCustomer(customerId);
        return Response.ok(requests).build();
    }

    // ==========================================
// GET /users/requests/pending
// M2 calls this to find unmatched requests
// ==========================================
    @GET
    @Path("/requests/pending")
    public Response getPendingRequests() {
        List<ServiceRequest> requests =
                serviceRequestBean.getPendingRequests();
        return Response.ok(requests).build();
    }

    // ==========================================
// PATCH /users/requests/{requestId}/match
// M2 calls this when a match is found
// Body: { "matchedOfferId": 10 }
// ==========================================
    @PATCH
    @Path("/requests/{requestId}/match")
    public Response updateMatch(@PathParam("requestId") Long requestId,
                                java.util.Map<String, Long> body) {
        Long matchedOfferId = body.get("matchedOfferId");
        if (matchedOfferId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"matchedOfferId is required\"}")
                    .build();
        }

        ServiceRequest updated = serviceRequestBean.updateMatch(requestId, matchedOfferId);
        if (updated == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"Request not found\"}")
                    .build();
        }

        return Response.ok(updated).build();
    }
}