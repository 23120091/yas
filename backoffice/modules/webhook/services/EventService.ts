import { WebhookEvent } from '../models/Event';
import apiClientService from '@commonServices/ApiClientService';

const baseUrl = '/api/webhook/backoffice/events';

export async function getEvents(): Promise<WebhookEvent[]> {
  const response = await apiClientService.get(baseUrl);
  if (response.status >= 200 && response.status < 300) {
    return response.json();
  }
  throw new Error(response.statusText);
}
