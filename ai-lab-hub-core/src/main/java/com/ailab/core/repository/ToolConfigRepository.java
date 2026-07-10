package com.ailab.core.repository;

import com.ailab.core.entity.ToolConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ToolConfigRepository extends JpaRepository<ToolConfig, Long> {
    Optional<ToolConfig> findByToolCodeAndConfigKey(String toolCode, String configKey);
}
