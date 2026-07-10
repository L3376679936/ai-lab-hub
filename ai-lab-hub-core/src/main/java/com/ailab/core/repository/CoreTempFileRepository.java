package com.ailab.core.repository;

import com.ailab.core.entity.CoreTempFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Date;
import java.util.List;

@Repository
public interface CoreTempFileRepository extends JpaRepository<CoreTempFile, Long> {
    List<CoreTempFile> findByStatusAndCreateTimeBefore(Integer status, Date createTime);
}
