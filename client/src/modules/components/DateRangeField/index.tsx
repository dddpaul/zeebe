/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useRef, useState} from 'react';
import {Field, useField, useForm} from 'react-final-form';
import {DateRangePopover} from './DateRangePopover';
import {formatDate, formatISODate, formatTime} from './formatDate';
import {TextField} from './styled';

type Props = {
  label: string;
  fromDateTimeKey: string;
  toDateTimeKey: string;
};

const formatInputValue = (fromDateTime?: Date, toDateTime?: Date) => {
  if (fromDateTime === undefined || toDateTime === undefined) {
    return '';
  }
  return `${formatDate(fromDateTime)} ${formatTime(
    fromDateTime
  )} - ${formatDate(toDateTime)} ${formatTime(toDateTime)}`;
};

const DateRangeField: React.FC<Props> = ({
  label,
  fromDateTimeKey,
  toDateTimeKey,
}) => {
  const cmTextFieldRef = useRef<HTMLCmTextfieldElement>(null);
  const textFieldRef = useRef<HTMLDivElement>(null);
  const form = useForm();
  const fromDateTime = useField<string>(fromDateTimeKey).input.value;
  const toDateTime = useField<string>(toDateTimeKey).input.value;

  const [isDateRangePopoverVisible, setIsDateRangePopoverVisible] =
    useState<boolean>(false);

  const getInputValue = () => {
    if (isDateRangePopoverVisible) {
      return 'Custom';
    }
    if (fromDateTime !== '' && toDateTime !== '') {
      return formatInputValue(new Date(fromDateTime), new Date(toDateTime));
    }
    return '';
  };

  const handleCancel = () => {
    setIsDateRangePopoverVisible(false);
  };

  return (
    <>
      <div ref={textFieldRef}>
        <TextField
          label={label}
          type="button"
          fieldSuffix={{
            type: 'icon',
            icon: 'calendar',
            press: () => {},
          }}
          value={getInputValue()}
          ref={cmTextFieldRef}
          readonly
          title={getInputValue()}
          onCmClick={() => {
            if (!isDateRangePopoverVisible) {
              setIsDateRangePopoverVisible(true);
            }
          }}
        />
        {[fromDateTimeKey, toDateTimeKey].map((filterKey) => (
          <Field
            name={filterKey}
            key={filterKey}
            component="input"
            type="hidden"
          />
        ))}
      </div>

      {isDateRangePopoverVisible && textFieldRef.current !== null && (
        <DateRangePopover
          referenceElement={textFieldRef.current}
          onCancel={handleCancel}
          onApply={({fromDateTime, toDateTime}) => {
            setIsDateRangePopoverVisible(false);
            form.change(fromDateTimeKey, formatISODate(fromDateTime));
            form.change(toDateTimeKey, formatISODate(toDateTime));
          }}
          onOutsideClick={(event) => {
            if (
              event.target instanceof Element &&
              cmTextFieldRef.current?.contains(event.target)
            ) {
              event.stopPropagation();
              event.preventDefault();
            }
            handleCancel();
          }}
          defaultValues={{
            fromDate:
              fromDateTime === '' ? '' : formatDate(new Date(fromDateTime)),
            fromTime:
              fromDateTime === '' ? '' : formatTime(new Date(fromDateTime)),
            toDate: toDateTime === '' ? '' : formatDate(new Date(toDateTime)),
            toTime: toDateTime === '' ? '' : formatTime(new Date(toDateTime)),
          }}
        />
      )}
    </>
  );
};

export {DateRangeField};
