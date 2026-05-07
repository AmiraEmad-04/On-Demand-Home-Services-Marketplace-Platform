package com.homehub.offerservice.repository;

import com.homehub.offerservice.entity.ServiceListing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ServiceListingRepository extends JpaRepository<ServiceListing, Long> {

    List<ServiceListing> findByStatus(ServiceListing.ListingStatus status);

    List<ServiceListing> findByProviderId(Long providerId);

    List<ServiceListing> findByProviderIdAndStatus(Long providerId, ServiceListing.ListingStatus status);

    /**
     * Filter by category and status.
     * Uses JPQL traversal: category.categoryId (matches the field name in ServiceCategory).
     */
    @Query("SELECT s FROM ServiceListing s WHERE s.category.categoryId = :categoryId AND s.status = :status")
    List<ServiceListing> findByCategoryAndStatus(
            @Param("categoryId") Long categoryId,
            @Param("status") ServiceListing.ListingStatus status
    );

    /**
     * Match a customer request: same category, OPEN, price <= maxPrice, date in range.
     */
    @Query("SELECT s FROM ServiceListing s WHERE s.category.categoryId = :categoryId " +
           "AND s.status = :status " +
           "AND s.price <= :maxPrice " +
           "AND s.availableDate BETWEEN :from AND :to")
    List<ServiceListing> findMatchingListings(
            @Param("categoryId") Long categoryId,
            @Param("status") ServiceListing.ListingStatus status,
            @Param("maxPrice") Double maxPrice,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
