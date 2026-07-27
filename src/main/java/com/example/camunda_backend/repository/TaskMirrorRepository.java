// 3. TaskMirrorRepository.java
package com.example.camunda_backend.repository;
import com.example.camunda_backend.entity.TaskMirror;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TaskMirrorRepository extends JpaRepository<TaskMirror, String> {
    List<TaskMirror> findByAssigneeAndStatus(String assignee, String status);
}