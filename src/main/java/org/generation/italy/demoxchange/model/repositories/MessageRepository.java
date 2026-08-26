package org.generation.italy.demoxchange.model.repositories;

import org.generation.italy.demoxchange.model.entities.Message;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {
}
