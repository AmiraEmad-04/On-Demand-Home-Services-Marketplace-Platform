package com.marketplace.walletservice.rest;

import com.marketplace.walletservice.beans.WalletSessionBean;
import com.marketplace.walletservice.entities.Wallet;
import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

/*
 * All endpoints start with /wallet
 * Full URL example:
 * http://localhost:8082/wallet-service/api/wallet/1/balance
 */
@Path("/wallet")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WalletResource {

    @EJB
    private WalletSessionBean walletBean;

    // ==========================================
    // POST /wallet/{userId}/create
    // Creates a new wallet for a user
    // Body: { "initialBalance": 200.00 }
    // Called when customer registers
    // ==========================================
    @POST
    @Path("/{userId}/create")
    public Response createWallet(@PathParam("userId") Long userId,
                                 Map<String, Double> body) {
        Double initialBalance = body.getOrDefault("initialBalance", 0.0);
        Wallet wallet = walletBean.createWallet(userId, initialBalance);

        if (wallet == null) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("{\"error\":\"Wallet already exists for this user\"}")
                    .build();
        }
        return Response.status(Response.Status.CREATED)
                .entity(wallet)
                .build();
    }

    // ==========================================
    // GET /wallet/{userId}/balance
    // Returns current wallet balance
    // Contract response: { "userId": 1, "balance": 200.00 }
    // ==========================================
    @GET
    @Path("/{userId}/balance")
    public Response getBalance(@PathParam("userId") Long userId) {
        Wallet wallet = walletBean.getWallet(userId);

        if (wallet == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"Wallet not found for userId: "
                            + userId + "\"}")
                    .build();
        }
        return Response.ok(wallet).build();
    }

    // ==========================================
    // POST /wallet/{userId}/topup
    // Adds money to wallet
    // Body: { "amount": 100.00 }
    // ==========================================
    @POST
    @Path("/{userId}/topup")
    public Response topUp(@PathParam("userId") Long userId,
                          Map<String, Double> body) {
        Double amount = body.get("amount");

        if (amount == null || amount <= 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"amount must be greater than 0\"}")
                    .build();
        }

        Wallet wallet = walletBean.topUp(userId, amount);

        if (wallet == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"Wallet not found for userId: "
                            + userId + "\"}")
                    .build();
        }
        return Response.ok(wallet).build();
    }

    // ==========================================
    // PATCH /wallet/{userId}/deduct
    // Deducts money from wallet
    // Body: { "amount": 80.00 }
    // Called by Booking Service (M2)
    // ==========================================
    @PATCH
    @Path("/{userId}/deduct")
    public Response deduct(@PathParam("userId") Long userId,
                           Map<String, Double> body) {
        Double amount = body.get("amount");

        if (amount == null || amount <= 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"amount must be greater than 0\"}")
                    .build();
        }

        Wallet wallet = walletBean.deduct(userId, amount);

        if (wallet == null) {
            return Response.status(Response.Status.PAYMENT_REQUIRED)
                    .entity("{\"error\":\"Insufficient balance\"}")
                    .build();
        }
        return Response.ok(wallet).build();
    }

    // ==========================================
    // PATCH /wallet/{userId}/rollback
    // Refunds money back to wallet
    // Body: { "amount": 80.00 }
    // Called by Booking Service if something fails
    // ==========================================
    @PATCH
    @Path("/{userId}/rollback")
    public Response rollback(@PathParam("userId") Long userId,
                             Map<String, Double> body) {
        Double amount = body.get("amount");

        if (amount == null || amount <= 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"amount must be greater than 0\"}")
                    .build();
        }

        Wallet wallet = walletBean.rollback(userId, amount);

        if (wallet == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"Wallet not found\"}")
                    .build();
        }
        return Response.ok(wallet).build();
    }
}
