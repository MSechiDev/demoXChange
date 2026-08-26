package org.generation.italy.demoxchange.model.repositories;

import org.generation.italy.demoxchange.model.entities.Review;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {
}
