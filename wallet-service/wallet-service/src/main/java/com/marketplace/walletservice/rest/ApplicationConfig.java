package com.marketplace.walletservice.rest;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/*
 * All REST endpoints start with /api
 * Full URL example:
 * http://localhost:8082/wallet-service/api/wallet/1/balance
 *
 * Breaking it down:
 * - localhost:8082       = JBoss with port offset 2
 * - wallet-service       = app name from pom.xml
 * - api                  = this class
 * - wallet/1/balance     = WalletResource @Path
 */
@ApplicationPath("/api")
public class ApplicationConfig extends Application {
}