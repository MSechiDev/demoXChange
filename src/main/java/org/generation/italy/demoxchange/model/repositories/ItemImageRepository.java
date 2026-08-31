package org.generation.italy.demoxchange.model.repositories;

import org.generation.italy.demoxchange.model.entities.ItemImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemImageRepository extends JpaRepository<ItemImage, Long> {

    List<ItemImage> findByItemIdOrderByDisplayOrderAsc(Long itemId);

    Optional<ItemImage> findByIdAndItemId(Long id, Long itemId);

    long countByItemId(Long itemId);
}
