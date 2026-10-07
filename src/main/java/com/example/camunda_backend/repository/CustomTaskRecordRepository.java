package com.example.camunda_backend.repository;

import com.example.camunda_backend.entity.CustomTaskRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomTaskRecordRepository extends JpaRepository<CustomTaskRecord, String> {

    List<CustomTaskRecord> findByProcessInstanceIdOrderByStartTimeAsc(String processInstanceId);

    List<CustomTaskRecord> findByAssigneeAndStatus(String assignee, String status);

    List<CustomTaskRecord> findByCandidateGroupAndStatus(String candidateGroup, String status);

    List<CustomTaskRecord> findByCompletedByOrderByCompletedTimeDesc(String completedBy);

    List<CustomTaskRecord> findByStatusOrderByStartTimeDesc(String status);
}
