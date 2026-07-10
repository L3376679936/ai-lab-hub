package com.ailab.core.entity;

import lombok.Data;
import javax.persistence.*;
import java.util.Date;

@Data
@Entity
@Table(name = "tool_config")
public class ToolConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tool_code", nullable = false, length = 50)
    private String toolCode;

    @Column(name = "config_key", nullable = false, length = 50)
    private String configKey;

    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    private String configValue;

    @Column(name = "update_time", insertable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateTime;
}
