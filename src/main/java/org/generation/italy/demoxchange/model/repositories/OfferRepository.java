package org.generation.italy.demoxchange.model.repositories;

import org.generation.italy.demoxchange.model.entities.Offer;
import org.generation.italy.demoxchange.model.entities.OfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OfferRepository extends JpaRepository<Offer, Long> {
    List<Offer> findByOffererId(Long offererId);
    List<Offer> findByListing_Item_Owner_Id(Long userId);
    List<Offer> findByListingIdAndStatus(Long listingId, OfferStatus status);
}
