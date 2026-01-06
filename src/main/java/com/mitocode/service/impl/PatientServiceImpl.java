package com.mitocode.service.impl;

import com.mitocode.model.Patient;
import com.mitocode.repo.IPatientRepo;
import com.mitocode.repo.PatientRepoImpl;
import com.mitocode.service.IPatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PatientServiceImpl implements IPatientService {

    //@Autowired
    private final IPatientRepo repo; // = new PatientRepo();

    @Override
    public Patient save(Patient patient) {
        return repo.save(patient);
    }

    @Override
    public Patient update(Integer id, Patient patient) {
        //VALIDAR EL ID con java reflexion
        return repo.save(patient);
    }

    @Override
    public List<Patient> findAll() {
        return repo.findAll();
    }

    @Override
    public Patient findById(Integer id) {
        return repo.findById(id).orElse(new Patient());
    }

    @Override
    public void delete(Integer id) {
        repo.deleteById(id);
    }

    /*
    public PatientService(PatientRepo repo) {
        this.repo = repo;
    }*/

    /*@Override
    public Patient validAndSave(Patient patient) {
        if(patient.getIdPatient() == 0) {
            return repo.save(patient);
        } else {
            return patient;
        }
    }*/
}
