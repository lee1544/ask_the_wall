package com.example.atw.client.repository;

import com.example.atw.client.entity.ChatModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatModelConfigRepository extends JpaRepository<ChatModelConfig, Long> {

    Optional<ChatModelConfig> findFirstByActiveTrue();

    List<ChatModelConfig> findAllByOrderByActiveDescUpdateTimeDescIdDesc();

    Optional<ChatModelConfig> findByConfigName(String configName);
}
