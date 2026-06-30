package com.mitocode.service;

import com.mitocode.model.Medic;

import java.util.List;

public interface IMedicService {

    Medic save(Medic medic);

    Medic update(Integer id, Medic medic);

    List<Medic> findAll();

    Medic findById(Integer id);

    void delete(Integer id);

}
