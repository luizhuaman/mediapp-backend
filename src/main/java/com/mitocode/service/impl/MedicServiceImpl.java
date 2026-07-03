package com.mitocode.service.impl;

import com.mitocode.exception.ModelNotFoundException;
import com.mitocode.model.Medic;
import com.mitocode.repo.IGenericRepo;
import com.mitocode.repo.IMedicRepo;
import com.mitocode.service.IMedicService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MedicServiceImpl extends CRUDImpl<Medic, Integer> implements IMedicService {

    //@Autowired
    private final IMedicRepo repo; // = new MedicRepo();

    @Override
    protected IGenericRepo<Medic, Integer> getRepo() {
        return repo;
    }

    @Override
    public List<Medic> getOldestMedics() {
        return null;
    }

    /*
    @Override
    public Medic save(Medic medic) {
        return repo.save(medic);
    }

    @Override
    public Medic update(Integer id, Medic medic) {
        //VALIDAR EL ID con java reflexion
        repo.findById(id).orElseThrow( () -> new ModelNotFoundException("ID NOT FOUND: " + id));
        return repo.save(medic);
    }

    @Override
    public List<Medic> findAll() {
        return repo.findAll();
    }

    @Override
    public Medic findById(Integer id) {
        return repo.findById(id).orElseThrow( () -> new ModelNotFoundException("ID NOT FOUND: " + id));
    }

    @Override
    public void delete(Integer id) {
        repo.findById(id).orElseThrow( () -> new ModelNotFoundException("ID NOT FOUND: " + id));
        repo.deleteById(id);
    }
    */


}
