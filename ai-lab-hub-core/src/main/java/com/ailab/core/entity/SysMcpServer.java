package com.ailab.core.entity;

import lombok.Data;
import javax.persistence.*;
import java.util.Date;

@Data
@Entity
@Table(name = "sys_mcp_server")
public class SysMcpServer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "server_name", unique = true, nullable = false, length = 100)
    private String serverName;

    @Column(name = "transport_type", nullable = false, length = 20)
    private String transportType;

    @Column(length = 255)
    private String command;

    @Column(length = 500)
    private String args;

    @Column(name = "env_vars", columnDefinition = "TEXT")
    private String envVars;

    @Column(name = "sse_url", length = 255)
    private String sseUrl;

    @Column(name = "tool_code", nullable = false, length = 50)
    private String toolCode;

    @Column(columnDefinition = "TINYINT DEFAULT 1")
    private Integer status;

    @Column(name = "create_time", insertable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createTime;

    @Column(name = "update_time", insertable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateTime;
}
