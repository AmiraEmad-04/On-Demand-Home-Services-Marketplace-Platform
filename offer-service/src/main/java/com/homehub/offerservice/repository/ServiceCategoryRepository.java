package com.homehub.offerservice.repository;

import com.homehub.offerservice.entity.ServiceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Long> {
    Optional<ServiceCategory> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
}
