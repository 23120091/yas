import { toastError } from '@commonServices/ToastService';
import PromotionGeneralInformation from 'modules/promotion/components/PromotionGeneralInformation';
import { PromotionDetail, PromotionDto } from 'modules/promotion/models/Promotion';
import { cancel, getPromotion, updatePromotion } from 'modules/promotion/services/PromotionService';
import { NextPage } from 'next';
import { useRouter } from 'next/router';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';

const PromotionUpdate: NextPage = () => {
  const router = useRouter();
  const { id } = router.query;

  const {
    register,
    handleSubmit,
    formState: { errors },
    setValue,
    trigger,
  } = useForm<PromotionDto>();

  const [isSubmittingForm, setIsSubmittingForm] = useState(false);

  const [promotion, setPromotion] = useState<PromotionDetail>();

  useEffect(() => {
    if (id) {
      getPromotion(+id)
        .then((data) => {
          setPromotion(data);
          setValue('slug', data.slug);
          setValue('couponCode', data.couponCode);
          setValue('name', data.name);
          setValue('applyTo', data.applyTo);
          setValue('startDate', removeTime(data.startDate));
          setValue('endDate', removeTime(data.endDate));
          setValue('discountAmount', data.discountAmount);
          setValue('discountPercentage', data.discountPercentage);
          setValue('usageLimit', data.usageLimit);
          setValue('usageType', data.usageType);
          setValue('discountType', data.discountType);
          setValue('minimumOrderPurchaseAmount', data.minimumOrderPurchaseAmount);
          setValue('description', data.description);
          setValue('brandIds', data.brands?.map((brand) => brand.id) ?? []);
          setValue('categoryIds', data.categories?.map((category) => category.id) ?? []);
          setValue('productIds', data.products?.map((product) => product.id) ?? []);
          setValue('isActive', data.isActive);
        })
        .catch((error) => {
          console.error('Failed to fetch promotion:', error);
          toastError(`Failed to load promotion ${id}`);
        });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const removeTime = (date: string) => {
    const DATE_PATTERN = /\d{4}-\d{2}-\d{2}/g;
    return date.match(DATE_PATTERN)![0];
  };

  const handleUpdatePromotion = async (dataForm: PromotionDto) => {
    dataForm.id = +id!;
    dataForm.discountAmount = dataForm.discountAmount ?? 0;
    dataForm.discountPercentage = dataForm.discountPercentage ?? 0;

    updatePromotion(dataForm).then((response) => {
      if (response.status === 200) {
        router.replace('/promotion/manager-promotion');
      }
    });
  };

  const submitUpdateForm = () => {
    setIsSubmittingForm(true);
    handleSubmit(handleUpdatePromotion)();
  };

  return (
    <div className="row mt-5">
      <div className="col-md-8">
        <h2>Update Promotion</h2>
        <form>
          <PromotionGeneralInformation
            register={register}
            errors={errors}
            setValue={setValue}
            trigger={trigger}
            promotion={promotion}
            isSubmitting={isSubmittingForm}
          />
          <div className="mt-5">
            <button
              className="btn btn-primary"
              style={{ marginRight: '20px' }}
              type="button"
              onClick={submitUpdateForm}
            >
              Save
            </button>
            <button className="btn btn-danger ml-4" type="button" onClick={cancel}>
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default PromotionUpdate;
