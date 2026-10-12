const amountFormat = new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })

export const formatAmount = (value) => amountFormat.format(Number(value))

// The API returns UTC timestamps without a zone suffix
export const formatTime = (value) =>
  new Date(/[zZ]|[+-]\d\d:\d\d$/.test(value) ? value : `${value}Z`).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  })
