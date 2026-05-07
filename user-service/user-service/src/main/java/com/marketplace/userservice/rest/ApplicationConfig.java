package com.marketplace.userservice.rest;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/*
 * This class tells JBoss:
 * "All REST endpoints in this app start with /api"
 *
 * So the full URL for register will be:
 * http://localhost:8080/user-service/api/users/register
 *
 * Breaking it down:
 * - localhost:8080     = JBoss server
 * - user-service       = our app name (from pom.xml finalName)
 * - api                = this class
 * - users/register     = UserResource @Path
 */
@ApplicationPath("/api")
public class ApplicationConfig extends Application {
    // Nothing needed here — JBoss auto-discovers our endpoints
}