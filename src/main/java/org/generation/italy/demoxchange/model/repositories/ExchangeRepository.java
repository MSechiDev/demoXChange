package org.generation.italy.demoxchange.model.repositories;

import org.generation.italy.demoxchange.model.entities.Exchange;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRepository extends JpaRepository<Exchange, Long> {
}
