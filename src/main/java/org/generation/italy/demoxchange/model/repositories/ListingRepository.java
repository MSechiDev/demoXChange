package org.generation.italy.demoxchange.model.repositories;

import org.generation.italy.demoxchange.model.entities.Listing;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingRepository extends JpaRepository<Listing, Long> {
}
