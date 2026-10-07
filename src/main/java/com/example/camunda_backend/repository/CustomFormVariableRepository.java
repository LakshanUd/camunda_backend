package com.example.camunda_backend.repository;

import com.example.camunda_backend.entity.CustomFormVariable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomFormVariableRepository extends JpaRepository<CustomFormVariable, Long> {

    List<CustomFormVariable> findByTaskId(String taskId);

    List<CustomFormVariable> findByProcessInstanceId(String processInstanceId);

    List<CustomFormVariable> findBySubmissionId(Long submissionId);
}
