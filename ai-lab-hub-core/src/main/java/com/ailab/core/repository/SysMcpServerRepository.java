package com.ailab.core.repository;

import com.ailab.core.entity.SysMcpServer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SysMcpServerRepository extends JpaRepository<SysMcpServer, Long> {
    Optional<SysMcpServer> findByToolCodeAndStatus(String toolCode, Integer status);
}
