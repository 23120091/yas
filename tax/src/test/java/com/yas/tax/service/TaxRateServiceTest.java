package com.yas.tax.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yas.commonlibrary.exception.NotFoundException;
import com.yas.tax.model.TaxClass;
import com.yas.tax.model.TaxRate;
import com.yas.tax.repository.TaxClassRepository;
import com.yas.tax.repository.TaxRateRepository;
import com.yas.tax.viewmodel.location.StateOrProvinceAndCountryGetNameVm;
import com.yas.tax.viewmodel.taxrate.TaxRateListGetVm;
import com.yas.tax.viewmodel.taxrate.TaxRatePostVm;
import com.yas.tax.viewmodel.taxrate.TaxRateVm;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
public class TaxRateServiceTest {

    @Mock
    private TaxRateRepository taxRateRepository;

    @Mock
    private TaxClassRepository taxClassRepository;

    @Mock
    private LocationService locationService;

    @InjectMocks
    private TaxRateService taxRateService;

    private TaxRate taxRate;
    private TaxClass taxClass;
    private TaxRatePostVm taxRatePostVm;

    @BeforeEach
    void setUp() {
        taxClass = new TaxClass();
        taxClass.setId(1L);
        taxClass.setName("Standard Tax");

        taxRate = new TaxRate();
        taxRate.setId(1L);
        taxRate.setRate(10.0);
        taxRate.setZipCode("12345");
        taxRate.setTaxClass(taxClass);
        taxRate.setStateOrProvinceId(2L);
        taxRate.setCountryId(3L);

        taxRatePostVm = new TaxRatePostVm(10.0, "12345", 1L, 2L, 3L);
    }

    @Test
    void createTaxRate_WhenValidInput_ShouldSaveAndReturnTaxRate() {
        when(taxClassRepository.existsById(1L)).thenReturn(true);
        when(taxClassRepository.getReferenceById(1L)).thenReturn(taxClass);
        when(taxRateRepository.save(any(TaxRate.class))).thenReturn(taxRate);

        TaxRate result = taxRateService.createTaxRate(taxRatePostVm);

        assertThat(result).isNotNull();
        assertThat(result.getRate()).isEqualTo(10.0);
        verify(taxRateRepository).save(any(TaxRate.class));
    }

    @Test
    void createTaxRate_WhenTaxClassIsInvalid_ShouldThrowNotFoundException() {
        when(taxClassRepository.existsById(1L)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> taxRateService.createTaxRate(taxRatePostVm));
    }

    @Test
    void updateTaxRate_WhenValidInput_ShouldUpdateTaxRate() {
        when(taxRateRepository.findById(1L)).thenReturn(Optional.of(taxRate));
        when(taxClassRepository.existsById(1L)).thenReturn(true);
        when(taxClassRepository.getReferenceById(1L)).thenReturn(taxClass);
        when(taxRateRepository.save(any(TaxRate.class))).thenReturn(taxRate);

        taxRateService.updateTaxRate(taxRatePostVm, 1L);

        verify(taxRateRepository).save(any(TaxRate.class));
    }

    @Test
    void updateTaxRate_WhenIdIsInvalid_ShouldThrowNotFoundException() {
        when(taxRateRepository.findById(2L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> taxRateService.updateTaxRate(taxRatePostVm, 2L));
    }

    @Test
    void delete_WhenIdIsValid_ShouldDeleteTaxRate() {
        when(taxRateRepository.existsById(1L)).thenReturn(true);
        doNothing().when(taxRateRepository).deleteById(1L);

        taxRateService.delete(1L);

        verify(taxRateRepository).deleteById(1L);
    }

    @Test
    void delete_WhenIdIsInvalid_ShouldThrowNotFoundException() {
        when(taxRateRepository.existsById(2L)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> taxRateService.delete(2L));
    }

    @Test
    void findById_WhenIdIsValid_ShouldReturnTaxRateVm() {
        when(taxRateRepository.findById(1L)).thenReturn(Optional.of(taxRate));

        TaxRateVm result = taxRateService.findById(1L);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.rate()).isEqualTo(10.0);
    }

    @Test
    void findAll_ShouldReturnListOfTaxRateVm() {
        when(taxRateRepository.findAll()).thenReturn(List.of(taxRate));

        List<TaxRateVm> result = taxRateService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(1L);
    }

    @Test
    void getPageableTaxRates_ShouldReturnTaxRateListGetVm() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<TaxRate> taxRatePage = new PageImpl<>(List.of(taxRate), pageable, 1);
        when(taxRateRepository.findAll(any(Pageable.class))).thenReturn(taxRatePage);

        StateOrProvinceAndCountryGetNameVm stateCountryVm = new StateOrProvinceAndCountryGetNameVm(2L, "State A", "Country A");
        when(locationService.getStateOrProvinceAndCountryNames(List.of(2L))).thenReturn(List.of(stateCountryVm));

        TaxRateListGetVm result = taxRateService.getPageableTaxRates(0, 10);

        assertThat(result).isNotNull();
        assertThat(result.taxRateGetDetailContent()).hasSize(1);
        assertThat(result.taxRateGetDetailContent().getFirst().stateOrProvinceName()).isEqualTo("State A");
        assertThat(result.taxRateGetDetailContent().getFirst().countryName()).isEqualTo("Country A");
    }

    @Test
    void getTaxPercent_WhenRecordExists_ShouldReturnRate() {
        when(taxRateRepository.getTaxPercent(3L, 2L, "12345", 1L)).thenReturn(10.0);

        double result = taxRateService.getTaxPercent(1L, 3L, 2L, "12345");

        assertThat(result).isEqualTo(10.0);
    }

    @Test
    void getTaxPercent_WhenRecordDoesNotExist_ShouldReturnZero() {
        when(taxRateRepository.getTaxPercent(3L, 2L, "12345", 1L)).thenReturn(null);

        double result = taxRateService.getTaxPercent(1L, 3L, 2L, "12345");

        assertThat(result).isEqualTo(0.0);
    }

    @Test
    void getBulkTaxRate_ShouldReturnListOfTaxRateVm() {
        List<Long> taxClassIds = List.of(1L);
        when(taxRateRepository.getBatchTaxRates(3L, 2L, "12345", new HashSet<>(taxClassIds)))
                .thenReturn(List.of(taxRate));

        List<TaxRateVm> result = taxRateService.getBulkTaxRate(taxClassIds, 3L, 2L, "12345");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(1L);
    }
}