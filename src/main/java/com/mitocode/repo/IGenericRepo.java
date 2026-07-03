package com.mitocode.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface IGenericRepo<T, ID> extends JpaRepository<T, ID> {
    // T Type, K Key, V value, E element
    // T proviene de la clase padre java.lang.Object
}
