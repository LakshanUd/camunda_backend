// 2. ProcessMirrorRepository.java
package com.example.camunda_backend.repository;
import com.example.camunda_backend.entity.ProcessMirror;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProcessMirrorRepository extends JpaRepository<ProcessMirror, String> {
    List<ProcessMirror> findByStatus(String status);
}