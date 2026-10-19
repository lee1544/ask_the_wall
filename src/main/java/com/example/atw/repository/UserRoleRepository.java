package com.example.atw.repository;

import com.example.atw.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Integer> {
    
    List<UserRole> findByUserId(Integer userId);
    
    List<UserRole> findByRoleId(Integer roleId);
    
    void deleteByUserId(Integer userId);
    
    @Query("SELECT ur.roleId FROM UserRole ur WHERE ur.userId = :userId")
    List<Integer> findRoleIdsByUserId(@Param("userId") Integer userId);
}
