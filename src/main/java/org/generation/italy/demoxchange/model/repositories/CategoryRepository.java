package org.generation.italy.demoxchange.model.repositories;

import org.generation.italy.demoxchange.model.entities.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
