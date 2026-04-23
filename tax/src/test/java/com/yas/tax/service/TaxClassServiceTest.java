package com.yas.tax.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yas.commonlibrary.exception.DuplicatedException;
import com.yas.commonlibrary.exception.NotFoundException;
import com.yas.tax.model.TaxClass;
import com.yas.tax.repository.TaxClassRepository;
import com.yas.tax.viewmodel.taxclass.TaxClassListGetVm;
import com.yas.tax.viewmodel.taxclass.TaxClassPostVm;
import com.yas.tax.viewmodel.taxclass.TaxClassVm;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
public class TaxClassServiceTest {

    @Mock
    private TaxClassRepository taxClassRepository;

    @InjectMocks
    private TaxClassService taxClassService;

    private TaxClass taxClass;
    private TaxClassPostVm taxClassPostVm;

    @BeforeEach
    void setUp() {
        taxClass = new TaxClass();
        taxClass.setId(1L);
        taxClass.setName("Standard Tax");

        taxClassPostVm = new TaxClassPostVm("1", "Standard Tax");
    }

    @Test
    void findAllTaxClasses_ShouldReturnListOfTaxClassVm() {
        when(taxClassRepository.findAll(any(Sort.class))).thenReturn(List.of(taxClass));

        List<TaxClassVm> result = taxClassService.findAllTaxClasses();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(1L);
        assertThat(result.getFirst().name()).isEqualTo("Standard Tax");
    }

    @Test
    void findById_WhenIdIsValid_ShouldReturnTaxClassVm() {
        when(taxClassRepository.findById(1L)).thenReturn(Optional.of(taxClass));

        TaxClassVm result = taxClassService.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Standard Tax");
    }

    @Test
    void findById_WhenIdIsInvalid_ShouldThrowNotFoundException() {
        when(taxClassRepository.findById(2L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> taxClassService.findById(2L));
    }

    @Test
    void create_WhenNameIsUnique_ShouldSaveAndReturnTaxClass() {
        when(taxClassRepository.existsByName(taxClassPostVm.name())).thenReturn(false);
        when(taxClassRepository.save(any(TaxClass.class))).thenReturn(taxClass);

        TaxClass result = taxClassService.create(taxClassPostVm);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Standard Tax");
        verify(taxClassRepository).save(any(TaxClass.class));
    }

    @Test
    void create_WhenNameIsDuplicated_ShouldThrowDuplicatedException() {
        when(taxClassRepository.existsByName(taxClassPostVm.name())).thenReturn(true);

        assertThrows(DuplicatedException.class, () -> taxClassService.create(taxClassPostVm));
    }

    @Test
    void update_WhenValidInput_ShouldUpdateTaxClass() {
        when(taxClassRepository.findById(1L)).thenReturn(Optional.of(taxClass));
        when(taxClassRepository.existsByNameNotUpdatingTaxClass(taxClassPostVm.name(), 1L)).thenReturn(false);
        when(taxClassRepository.save(any(TaxClass.class))).thenReturn(taxClass);

        taxClassService.update(taxClassPostVm, 1L);

        verify(taxClassRepository).save(taxClass);
    }

    @Test
    void update_WhenIdIsInvalid_ShouldThrowNotFoundException() {
        when(taxClassRepository.findById(2L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> taxClassService.update(taxClassPostVm, 2L));
    }

    @Test
    void update_WhenNameIsDuplicated_ShouldThrowDuplicatedException() {
        when(taxClassRepository.findById(1L)).thenReturn(Optional.of(taxClass));
        when(taxClassRepository.existsByNameNotUpdatingTaxClass(taxClassPostVm.name(), 1L)).thenReturn(true);

        assertThrows(DuplicatedException.class, () -> taxClassService.update(taxClassPostVm, 1L));
    }

    @Test
    void delete_WhenIdIsValid_ShouldDeleteTaxClass() {
        when(taxClassRepository.existsById(1L)).thenReturn(true);
        doNothing().when(taxClassRepository).deleteById(1L);

        taxClassService.delete(1L);

        verify(taxClassRepository).deleteById(1L);
    }

    @Test
    void delete_WhenIdIsInvalid_ShouldThrowNotFoundException() {
        when(taxClassRepository.existsById(2L)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> taxClassService.delete(2L));
    }

    @Test
    void getPageableTaxClasses_ShouldReturnTaxClassListGetVm() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<TaxClass> taxClassPage = new PageImpl<>(List.of(taxClass), pageable, 1);

        when(taxClassRepository.findAll(any(Pageable.class))).thenReturn(taxClassPage);

        TaxClassListGetVm result = taxClassService.getPageableTaxClasses(0, 10);

        assertThat(result).isNotNull();
        assertThat(result.taxClassContent()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
    }
}