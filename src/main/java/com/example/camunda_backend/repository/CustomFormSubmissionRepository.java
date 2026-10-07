package com.example.camunda_backend.repository;

import com.example.camunda_backend.entity.CustomFormSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomFormSubmissionRepository extends JpaRepository<CustomFormSubmission, Long> {

    Optional<CustomFormSubmission> findFirstByTaskIdOrderBySubmittedAtDesc(String taskId);

    List<CustomFormSubmission> findByProcessInstanceIdOrderBySubmittedAtAsc(String processInstanceId);

    List<CustomFormSubmission> findBySubmittedByOrderBySubmittedAtDesc(String submittedBy);

    boolean existsByTaskId(String taskId);
}
