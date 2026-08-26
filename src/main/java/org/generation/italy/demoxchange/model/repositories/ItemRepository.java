package org.generation.italy.demoxchange.model.repositories;

import org.generation.italy.demoxchange.model.entities.Item;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {
}
