package com.marketplace.userservice.beans;

import com.marketplace.userservice.entities.ServiceRequest;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

/*
 * @Stateless bean — handles all service request DB operations.
 * Customer submits a request → we store it → M2 matches it later.
 */
@Stateless
public class ServiceRequestBean {

    @PersistenceContext(unitName = "UsersPU")
    private EntityManager em;

    /*
     * Save a new service request from a customer.
     */
    public ServiceRequest createRequest(Long customerId, String category,
                                        Double maxPrice, String requiredDate) {
        ServiceRequest request = new ServiceRequest(
                customerId, category, maxPrice, requiredDate);
        em.persist(request);
        return request;
    }

    /*
     * Get all requests made by a specific customer.
     */
    public List<ServiceRequest> getRequestsByCustomer(Long customerId) {
        return em.createQuery(
                        "SELECT r FROM ServiceRequest r WHERE r.customerId = :customerId",
                        ServiceRequest.class)
                .setParameter("customerId", customerId)
                .getResultList();
    }

    /*
     * Get all pending requests — M2 calls this to find requests to match.
     */
    public List<ServiceRequest> getPendingRequests() {
        return em.createQuery(
                        "SELECT r FROM ServiceRequest r WHERE r.status = 'PENDING'",
                        ServiceRequest.class)
                .getResultList();
    }

    /*
     * Update request status — called when M2 finds a match.
     */
    public ServiceRequest updateMatch(Long requestId, Long matchedOfferId) {
        ServiceRequest request = em.find(ServiceRequest.class, requestId);
        if (request == null) return null;
        request.setStatus("MATCHED");
        request.setMatchedOfferId(matchedOfferId);
        em.merge(request);
        return request;
    }
}