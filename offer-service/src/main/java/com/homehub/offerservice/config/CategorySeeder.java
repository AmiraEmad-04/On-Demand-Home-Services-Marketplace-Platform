package com.homehub.offerservice.config;

import com.homehub.offerservice.entity.ServiceCategory;
import com.homehub.offerservice.repository.ServiceCategoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds default service categories into the DB when the app starts.
 * Categories are configured in application.properties under app.seed-categories.
 */
@Component
public class CategorySeeder implements ApplicationRunner {

    private final ServiceCategoryRepository categoryRepo;

    @Value("${app.seed-categories:Plumbing,Carpentry,Electrical,Cleaning,Painting}")
    private String seedCategories;

    public CategorySeeder(ServiceCategoryRepository categoryRepo) {
        this.categoryRepo = categoryRepo;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String raw : seedCategories.split(",")) {
            String name = raw.trim();
            if (!name.isEmpty() && !categoryRepo.existsByNameIgnoreCase(name)) {
                categoryRepo.save(new ServiceCategory(name));
                System.out.println("[Seeder] Added category: " + name);
            }
        }
    }
}
